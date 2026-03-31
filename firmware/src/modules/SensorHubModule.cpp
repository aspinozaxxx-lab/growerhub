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
static const uint32_t kSensorBootGraceMs = 30000;
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
  boot_started_ms_ = 0;
  for (size_t i = 0; i < Drivers::Rj9PortScanner::kMaxPorts; ++i) {
    soil_status_[i] = SensorStatus::kDisconnected;
    soil_seen_once_[i] = false;
  }
  dht_status_ = SensorStatus::kOk;
  dht_seen_once_ = false;
  ResetDhtErrorEpisode();
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
  out->status = dht_status_;
  return true;
}

SensorHubModule::SensorStatus SensorHubModule::GetSoilPortStatus(uint8_t port) const {
  if (port >= Drivers::Rj9PortScanner::kMaxPorts) {
    return SensorStatus::kDisconnected;
  }
  return soil_status_[port];
}

const char* SensorHubModule::StatusToString(SensorStatus status) {
  switch (status) {
    case SensorStatus::kError:
      return "ERROR";
    case SensorStatus::kDisconnected:
      return "DISCONNECTED";
    case SensorStatus::kOk:
    default:
      return "OK";
  }
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
  UpdateSoilStatuses(now_ms);
}

void SensorHubModule::ReadDht(uint32_t now_ms) {
  float temperature = 0.0f;
  float humidity = 0.0f;
  const SensorStatus prev_status = dht_status_;
  const bool ok = dht_.Read(now_ms, &temperature, &humidity);
  if (ok) {
    dht_available_ = true;
    dht_status_ = SensorStatus::kOk;
    dht_seen_once_ = true;
    dht_temp_c_ = temperature;
    dht_humidity_ = humidity;
    dht_fail_count_ = 0;
    ResetDhtErrorEpisode();
    return;
  }

  dht_available_ = false;
  UpdateDhtStatusOnFailure(now_ms, dht_.GetLastError());
  if (dht_status_ == SensorStatus::kError && prev_status != SensorStatus::kError) {
    char log_buf[192];
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[SENSOR] oshibka chteniya air datchika pin=%u code=%s",
                  static_cast<unsigned int>(dht_.GetPin()),
                  dht_.GetLastErrorCode());
    Util::Logger::Info(log_buf);
  }
  if (dht_fail_count_ < 0xFF) {
    dht_fail_count_++;
  }
  if (dht_status_ != SensorStatus::kError) {
    dht_reboot_pending_ = false;
    return;
  }
  if (!dht_error_event_sent_) {
    PublishSensorReadErrorEvent(now_ms, dht_.GetLastErrorCode());
  }
  if (!dht_auto_reboot_ || dht_fail_count_ < kDhtFailThreshold) {
    return;
  }
  if (last_dht_reboot_ms_ != 0 && now_ms - last_dht_reboot_ms_ < kDhtRebootCooldownMs) {
    return;
  }
  if (pump_blocked_) {
    dht_reboot_pending_ = true;
    return;
  }
  RequestReboot(now_ms);
}

void SensorHubModule::UpdateSoilStatuses(uint32_t now_ms) {
  const size_t port_count = scanner_.GetPortCount();
  const bool grace_elapsed = now_ms >= boot_started_ms_ + kSensorBootGraceMs;
  for (size_t port = 0; port < Drivers::Rj9PortScanner::kMaxPorts; ++port) {
    if (port >= port_count) {
      soil_status_[port] = SensorStatus::kDisconnected;
      continue;
    }
    const bool detected = scanner_.IsDetected(static_cast<uint8_t>(port));
    if (detected) {
      soil_seen_once_[port] = true;
      soil_status_[port] = SensorStatus::kOk;
      continue;
    }
    if (soil_seen_once_[port] || grace_elapsed) {
      soil_status_[port] = SensorStatus::kDisconnected;
    }
  }
}

void SensorHubModule::UpdateDhtStatusOnFailure(uint32_t now_ms, Drivers::Dht22Sensor::ReadError error) {
  const bool grace_elapsed = now_ms >= boot_started_ms_ + kSensorBootGraceMs;
  if (dht_seen_once_) {
    dht_status_ = SensorStatus::kError;
    return;
  }
  switch (error) {
    case Drivers::Dht22Sensor::ReadError::kChecksum:
    case Drivers::Dht22Sensor::ReadError::kInvalidFrame:
    case Drivers::Dht22Sensor::ReadError::kReadFailed:
      dht_status_ = SensorStatus::kError;
      return;
    case Drivers::Dht22Sensor::ReadError::kTimeout:
      dht_status_ = grace_elapsed ? SensorStatus::kDisconnected : SensorStatus::kOk;
      return;
    case Drivers::Dht22Sensor::ReadError::kNoResponse:
    case Drivers::Dht22Sensor::ReadError::kNone:
    default:
      dht_status_ = grace_elapsed ? SensorStatus::kDisconnected : SensorStatus::kOk;
      return;
  }
}

void SensorHubModule::PublishSensorReadErrorEvent(uint32_t now_ms, const char* error_code) {
  if (dht_failure_id_[0] == '\0') {
    dht_failure_seq_++;
    std::snprintf(dht_failure_id_, sizeof(dht_failure_id_), "dht-%lu", static_cast<unsigned long>(dht_failure_seq_));
  }
  PublishServiceEvent(
      "SENSOR_READ_ERROR",
      now_ms,
      dht_failure_id_,
      error_code,
      dht_auto_reboot_,
      dht_fail_count_);
  dht_error_event_sent_ = true;
}

void SensorHubModule::PublishRebootEvent(uint32_t now_ms) {
  PublishServiceEvent(
      "DEVICE_REBOOT_SENSOR_FAILURE",
      now_ms,
      dht_failure_id_,
      dht_.GetLastErrorCode(),
      true,
      dht_fail_count_);
}

bool SensorHubModule::BuildIsoTimestamp(char* out, size_t out_size) const {
  if (!out || out_size == 0 || !time_) {
    return false;
  }
  Services::TimeFields fields{};
  if (!time_->GetTime(&fields)) {
    return false;
  }
  std::snprintf(out,
                out_size,
                "%04u-%02u-%02uT%02u:%02u:%02uZ",
                static_cast<unsigned int>(fields.year),
                static_cast<unsigned int>(fields.month),
                static_cast<unsigned int>(fields.day),
                static_cast<unsigned int>(fields.hour),
                static_cast<unsigned int>(fields.minute),
                static_cast<unsigned int>(fields.second));
  return true;
}

void SensorHubModule::PublishServiceEvent(
    const char* event_type,
    uint32_t now_ms,
    const char* failure_id,
    const char* error_code,
    bool auto_reboot,
    uint8_t errors_count) {
  (void)now_ms;
  if (!mqtt_ || !mqtt_->IsConnected() || !device_id_ || !event_type) {
    return;
  }
  char topic[128];
  if (!Services::Topics::BuildEventsTopic(topic, sizeof(topic), device_id_)) {
    return;
  }
  char ts[32];
  const bool has_ts = BuildIsoTimestamp(ts, sizeof(ts));
  char payload[384];
  std::snprintf(payload,
                sizeof(payload),
                "{\"type\":\"%s\",\"ts\":%s,\"failure_id\":%s,\"sensor_scope\":\"air\",\"sensor_type\":\"AIR\",\"channel\":0,\"error_code\":%s,\"auto_reboot\":%s,\"errors_count\":%u}",
                event_type,
                has_ts ? "\"" : "null",
                failure_id && failure_id[0] != '\0' ? "\"" : "null",
                error_code && error_code[0] != '\0' ? "\"" : "null",
                auto_reboot ? "true" : "false",
                static_cast<unsigned int>(errors_count));
  if (has_ts) {
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"type\":\"%s\",\"ts\":\"%s\",\"failure_id\":%s%s%s,\"sensor_scope\":\"air\",\"sensor_type\":\"AIR\",\"channel\":0,\"error_code\":%s%s%s,\"auto_reboot\":%s,\"errors_count\":%u}",
                  event_type,
                  ts,
                  failure_id && failure_id[0] != '\0' ? "\"" : "null",
                  failure_id && failure_id[0] != '\0' ? failure_id : "",
                  failure_id && failure_id[0] != '\0' ? "\"" : "",
                  error_code && error_code[0] != '\0' ? "\"" : "null",
                  error_code && error_code[0] != '\0' ? error_code : "",
                  error_code && error_code[0] != '\0' ? "\"" : "",
                  auto_reboot ? "true" : "false",
                  static_cast<unsigned int>(errors_count));
  } else {
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"type\":\"%s\",\"ts\":null,\"failure_id\":%s%s%s,\"sensor_scope\":\"air\",\"sensor_type\":\"AIR\",\"channel\":0,\"error_code\":%s%s%s,\"auto_reboot\":%s,\"errors_count\":%u}",
                  event_type,
                  failure_id && failure_id[0] != '\0' ? "\"" : "null",
                  failure_id && failure_id[0] != '\0' ? failure_id : "",
                  failure_id && failure_id[0] != '\0' ? "\"" : "",
                  error_code && error_code[0] != '\0' ? "\"" : "null",
                  error_code && error_code[0] != '\0' ? error_code : "",
                  error_code && error_code[0] != '\0' ? "\"" : "",
                  auto_reboot ? "true" : "false",
                  static_cast<unsigned int>(errors_count));
  }
  mqtt_->Publish(topic, payload, false, 1);
}

void SensorHubModule::ResetDhtErrorEpisode() {
  dht_error_event_sent_ = false;
  dht_failure_id_[0] = '\0';
}

void SensorHubModule::RequestReboot(uint32_t now_ms) {
  if (!event_queue_) {
    return;
  }
  PublishRebootEvent(now_ms);
  Core::Event event{};
  event.type = Core::EventType::kRebootRequest;
  event.value = now_ms;
  event.mqtt.topic[0] = '\0';
  std::snprintf(event.mqtt.payload, sizeof(event.mqtt.payload), "dht22_error");
  event_queue_->Push(event);

  dht_reboot_pending_ = false;
  last_dht_reboot_ms_ = now_ms;
  dht_fail_count_ = 0;
  ResetDhtErrorEpisode();
}

}
