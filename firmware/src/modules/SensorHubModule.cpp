/*
 * Chto v faile: realizaciya modulya sbora dannyh datchikov.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/SensorHubModule.h"

#include <cstdio>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"
#include "services/MqttService.h"
#include "services/TimeService.h"
#include "services/Topics.h"
#include "util/Logger.h"

namespace Modules {

static const uint32_t kScanIntervalMs = 5000;
static const uint32_t kRescanDelayMs = 2000;
static const uint32_t kDhtReadIntervalMs = 5000;
static const uint8_t kDhtFailThreshold = 3;
static const uint32_t kDhtRebootCooldownMs = 300000;

void SensorHubModule::Init(Core::Context& ctx) {
  const Config::HardwareProfile& profile = ctx.hardware ? *ctx.hardware : Config::GetHardwareProfile();
  scanner_.Init(profile.pins.soil_adc_pins, profile.soil_port_count);
  event_queue_ = ctx.event_queue;
  mqtt_ = ctx.mqtt;
  time_ = ctx.time;
  device_id_ = ctx.device_id;
  dht_enabled_ = profile.has_dht22;
  dht_auto_reboot_ = profile.dht_auto_reboot_on_fail;
  if (dht_enabled_) {
    dht_.Init(profile.pins.dht_pin);
  }

  if (ctx.scheduler) {
    ctx.scheduler->AddPeriodic("soil_scan", kScanIntervalMs, &SensorHubModule::ScanTask);
  }

  Util::Logger::Info("init SensorHubModule");
}

void SensorHubModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  if (event.type == Core::EventType::kPumpStarted) {
    pump_blocked_ = true;
    rescan_pending_ = false;
    return;
  }
  if (event.type == Core::EventType::kPumpStopped) {
    pump_blocked_ = false;
    rescan_pending_ = true;
    rescan_at_ms_ = 0;
    return;
  }
}

void SensorHubModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  if (dht_enabled_) {
    if (last_dht_read_ms_ == 0 || now_ms - last_dht_read_ms_ >= kDhtReadIntervalMs) {
      ReadDht(now_ms);
      last_dht_read_ms_ = now_ms;
    }
  }

  if (rescan_pending_) {
    if (rescan_at_ms_ == 0) {
      rescan_at_ms_ = now_ms + kRescanDelayMs;
    }
    if (now_ms >= rescan_at_ms_) {
      rescan_pending_ = false;
      ScanNow(now_ms);
    }
  }

  if (dht_reboot_pending_ && !pump_blocked_) {
    RequestReboot(now_ms);
  }
}

const Drivers::Rj9PortScanner* SensorHubModule::GetScanner() const {
  return &scanner_;
}

Drivers::Rj9PortScanner* SensorHubModule::GetScanner() {
  return &scanner_;
}

bool SensorHubModule::GetDhtReading(DhtReading* out) const {
  if (!out || !dht_enabled_) {
    return false;
  }
  out->available = dht_available_;
  out->temperature_c = dht_temp_c_;
  out->humidity = dht_humidity_;
  return true;
}

#if defined(UNIT_TEST)
Drivers::Dht22Sensor* SensorHubModule::GetDhtSensor() {
  return &dht_;
}
#endif

void SensorHubModule::ScanTask(Core::Context& ctx, uint32_t now_ms) {
  if (!ctx.sensor_hub) {
    return;
  }
  ctx.sensor_hub->ScanNow(now_ms);
}

void SensorHubModule::ScanNow(uint32_t now_ms) {
  if (pump_blocked_) {
    return;
  }
  scanner_.Scan();
  last_scan_ms_ = now_ms;
}

void SensorHubModule::ReadDht(uint32_t now_ms) {
  float temperature = 0.0f;
  float humidity = 0.0f;
  const bool ok = dht_.Read(now_ms, &temperature, &humidity);
  if (ok) {
    dht_available_ = true;
    dht_temp_c_ = temperature;
    dht_humidity_ = humidity;
    dht_fail_count_ = 0;
    return;
  }

  dht_available_ = false;
  if (dht_fail_count_ < 0xFF) {
    dht_fail_count_++;
  }
  if (!dht_auto_reboot_ || dht_fail_count_ < kDhtFailThreshold) {
    return;
  }
  if (last_dht_reboot_ms_ != 0 && now_ms - last_dht_reboot_ms_ < kDhtRebootCooldownMs) {
    return;
  }
  // DHT error: reboot otklyuchen, tol'ko sbros pending flagov.
  dht_reboot_pending_ = false;
  dht_event_pending_ = false;
  (void)now_ms;
}

void SensorHubModule::RequestReboot(uint32_t now_ms) {
  if (!event_queue_) {
    return;
  }
  const uint8_t errors_count = dht_fail_count_;
  Core::Event event{};
  event.type = Core::EventType::kRebootRequest;
  event.value = now_ms;
  event.mqtt.topic[0] = '\0';
  std::snprintf(event.mqtt.payload, sizeof(event.mqtt.payload), "dht22_error");
  event_queue_->Push(event);

  dht_reboot_pending_ = false;
  last_dht_reboot_ms_ = now_ms;
  PublishDhtFailEvent(now_ms, errors_count);
  dht_event_pending_ = false;
  dht_fail_count_ = 0;
}

void SensorHubModule::PublishDhtFailEvent(uint32_t now_ms, uint8_t errors_count) {
  if (!mqtt_ || !mqtt_->IsConnected() || !device_id_) {
    return;
  }
  char topic[128];
  if (!Services::Topics::BuildEventsTopic(topic, sizeof(topic), device_id_)) {
    return;
  }

  char ts[32];
  bool has_ts = false;
  if (time_) {
    Services::TimeFields fields{};
    if (time_->GetTime(&fields)) {
      std::snprintf(ts,
                    sizeof(ts),
                    "%04u-%02u-%02uT%02u:%02u:%02uZ",
                    static_cast<unsigned int>(fields.year),
                    static_cast<unsigned int>(fields.month),
                    static_cast<unsigned int>(fields.day),
                    static_cast<unsigned int>(fields.hour),
                    static_cast<unsigned int>(fields.minute),
                    static_cast<unsigned int>(fields.second));
      has_ts = true;
    }
  }

  char payload[160];
  if (has_ts) {
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"type\":\"sensor.dht22.fail\",\"ts\":\"%s\",\"errors_count\":%u}",
                  ts,
                  static_cast<unsigned int>(errors_count));
  } else {
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"type\":\"sensor.dht22.fail\",\"ts\":null,\"errors_count\":%u}",
                  static_cast<unsigned int>(errors_count));
  }
  mqtt_->Publish(topic, payload, false, 1);
}

}
