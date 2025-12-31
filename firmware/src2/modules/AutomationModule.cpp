/*
 * Chto v faile: realizaciya modulya avtomatizacii poliva i sveta.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/AutomationModule.h"

#include <cstdio>
#include <cstring>

#include "core/Context.h"
#include "drivers/soil/Rj9PortScanner.h"
#include "modules/ActuatorModule.h"
#include "modules/ConfigSyncModule.h"
#include "modules/SensorHubModule.h"
#include "services/MqttService.h"
#include "services/TimeService.h"
#include "services/Topics.h"
#include "util/Logger.h"

namespace Modules {

static uint16_t ToMinutes(uint16_t hhmm) {
  return static_cast<uint16_t>((hhmm / 100) * 60 + (hhmm % 100));
}

void AutomationModule::Init(Core::Context& ctx) {
  mqtt_ = ctx.mqtt;
  time_ = ctx.time;
  actuator_ = ctx.actuator;
  config_sync_ = ctx.config_sync;
  sensor_hub_ = ctx.sensor_hub;
  device_id_ = ctx.device_id;
  config_ = Util::DefaultScenariosConfig();
  std::memset(schedule_fired_, 0, sizeof(schedule_fired_));
  last_light_on_ = false;
  Util::Logger::Info("init AutomationModule");
  RefreshConfig();
}

void AutomationModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  if (event.type == Core::EventType::kConfigUpdated) {
    RefreshConfig();
  }
}

void AutomationModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  HandleMoisture(now_ms);
  HandleWaterSchedule(now_ms);
  HandleLightSchedule(now_ms);
}

void AutomationModule::RefreshConfig() {
  if (config_sync_) {
    config_ = config_sync_->GetConfig();
  } else {
    config_ = Util::DefaultScenariosConfig();
  }
}

void AutomationModule::HandleMoisture(uint32_t now_ms) {
  if (!config_.water_moisture.enabled || !actuator_ || !sensor_hub_) {
    return;
  }
  if (!CanWaterNow(now_ms)) {
    return;
  }
  const Drivers::Rj9PortScanner* scanner = sensor_hub_->GetScanner();
  if (!scanner) {
    return;
  }

  for (size_t i = 0; i < Util::kMaxSoilSensors; ++i) {
    const Util::MoistureSensorConfig& sensor_cfg = config_.water_moisture.sensors[i];
    if (!sensor_cfg.enabled) {
      continue;
    }
    const uint8_t port = sensor_cfg.port;
    if (!scanner->IsDetected(port)) {
      continue;
    }
    const uint8_t percent = scanner->GetLastPercent(port);
    if (percent >= sensor_cfg.threshold_percent) {
      continue;
    }
    if (!actuator_->StartPump(sensor_cfg.duration_s, "auto.moisture")) {
      return;
    }
    MarkWatered(now_ms);
    PublishEvent("moisture", port, sensor_cfg.duration_s, percent);
    return;
  }
}

void AutomationModule::HandleWaterSchedule(uint32_t now_ms) {
  if (!config_.water_schedule.enabled || !actuator_) {
    return;
  }
  if (!CanWaterNow(now_ms)) {
    return;
  }
  if (!time_) {
    return;
  }
  Services::TimeFields fields{};
  if (!time_->GetTime(&fields)) {
    return;
  }

  const uint32_t day_key = BuildDayKey(fields);
  const uint16_t now_minutes = static_cast<uint16_t>(fields.hour * 60 + fields.minute);
  for (size_t i = 0; i < config_.water_schedule.entry_count; ++i) {
    const Util::WaterScheduleEntry& entry = config_.water_schedule.entries[i];
    if (!DayMatches(fields.wday, entry.days_mask)) {
      continue;
    }
    if (ToMinutes(entry.start_hhmm) != now_minutes) {
      continue;
    }
    const uint32_t fire_key = day_key * 10000U + entry.start_hhmm;
    if (schedule_fired_[i] == fire_key) {
      continue;
    }
    if (!actuator_->StartPump(entry.duration_s, "auto.schedule")) {
      return;
    }
    schedule_fired_[i] = fire_key;
    MarkWatered(now_ms);
    PublishEvent("schedule", 0, entry.duration_s, 0);
    return;
  }
}

void AutomationModule::HandleLightSchedule(uint32_t now_ms) {
  (void)now_ms;
  if (!config_.light_schedule.enabled || !actuator_) {
    return;
  }
  if (!time_) {
    return;
  }
  Services::TimeFields fields{};
  if (!time_->GetTime(&fields)) {
    return;
  }

  const uint16_t now_minutes = static_cast<uint16_t>(fields.hour * 60 + fields.minute);
  bool should_be_on = false;
  for (size_t i = 0; i < config_.light_schedule.entry_count; ++i) {
    const Util::LightScheduleEntry& entry = config_.light_schedule.entries[i];
    uint16_t start_minutes = ToMinutes(entry.start_hhmm);
    uint16_t end_minutes = ToMinutes(entry.end_hhmm);
    uint8_t wday = fields.wday;
    if (start_minutes > end_minutes && now_minutes < end_minutes) {
      wday = (wday + 6) % 7;
    }
    if (!DayMatches(wday, entry.days_mask)) {
      continue;
    }
    if (start_minutes <= end_minutes) {
      if (now_minutes >= start_minutes && now_minutes < end_minutes) {
        should_be_on = true;
        break;
      }
    } else {
      if (now_minutes >= start_minutes || now_minutes < end_minutes) {
        should_be_on = true;
        break;
      }
    }
  }

  if (should_be_on != last_light_on_) {
    actuator_->SetLight(should_be_on);
    last_light_on_ = should_be_on;
  }
}

bool AutomationModule::CanWaterNow(uint32_t now_ms) const {
  if (!actuator_ || actuator_->IsPumpRunning()) {
    return false;
  }
  const uint32_t min_spacing_ms =
      config_.water_moisture.min_time_between_watering_s > 0
          ? config_.water_moisture.min_time_between_watering_s * 1000U
          : 0;
  if (min_spacing_ms == 0) {
    return true;
  }
  return last_auto_water_ms_ == 0 || now_ms - last_auto_water_ms_ >= min_spacing_ms;
}

void AutomationModule::MarkWatered(uint32_t now_ms) {
  last_auto_water_ms_ = now_ms;
}

void AutomationModule::PublishEvent(const char* mode, uint8_t port, uint32_t duration_s, uint8_t soil_percent) {
  if (!mqtt_ || !device_id_) {
    return;
  }
  char topic[128];
  if (!Services::Topics::BuildEventsTopic(topic, sizeof(topic), device_id_)) {
    return;
  }
  uint64_t event_ms = 0;
  if (!time_ || !time_->TryGetUnixTimeMs(&event_ms)) {
    return;
  }
  if (last_event_ms_ != 0 && event_ms - last_event_ms_ < kMinEventSpacingMs) {
    return;
  }
  last_event_ms_ = event_ms;

  char event_id[96];
  std::snprintf(event_id, sizeof(event_id), "%s-%llu", device_id_,
                static_cast<unsigned long long>(event_ms));

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

  char payload[256];
  if (has_ts) {
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"type\":\"watering.auto\",\"mode\":\"%s\",\"port\":%u,\"duration_s\":%u,\"soil_percent\":%u,\"ts\":\"%s\",\"event_id\":\"%s\"}",
                  mode ? mode : "",
                  static_cast<unsigned int>(port),
                  static_cast<unsigned int>(duration_s),
                  static_cast<unsigned int>(soil_percent),
                  ts,
                  event_id);
  } else {
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"type\":\"watering.auto\",\"mode\":\"%s\",\"port\":%u,\"duration_s\":%u,\"soil_percent\":%u,\"ts\":null,\"event_id\":\"%s\"}",
                  mode ? mode : "",
                  static_cast<unsigned int>(port),
                  static_cast<unsigned int>(duration_s),
                  static_cast<unsigned int>(soil_percent),
                  event_id);
  }

  mqtt_->Publish(topic, payload, false, 1);
}

uint32_t AutomationModule::BuildDayKey(const Services::TimeFields& fields) const {
  return static_cast<uint32_t>(fields.year) * 10000U +
         static_cast<uint32_t>(fields.month) * 100U +
         static_cast<uint32_t>(fields.day);
}

bool AutomationModule::DayMatches(uint8_t wday, uint8_t mask) const {
  if (wday > 6) {
    return false;
  }
  return (mask & (1U << wday)) != 0;
}

}
