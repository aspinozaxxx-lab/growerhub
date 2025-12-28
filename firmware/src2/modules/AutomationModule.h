#pragma once

#include <cstdint>

#include "core/Module.h"
#include "util/JsonUtil.h"

namespace Services {
class MqttService;
class TimeService;
struct TimeFields;
}

namespace Modules {
class ActuatorModule;
class ConfigSyncModule;
class SensorHubModule;
}

namespace Modules {

class AutomationModule : public Core::Module {
 public:
  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

 private:
  static const uint32_t kMinEventSpacingMs = 1000;

  void RefreshConfig();
  void HandleMoisture(uint32_t now_ms);
  void HandleWaterSchedule(uint32_t now_ms);
  void HandleLightSchedule(uint32_t now_ms);
  bool CanWaterNow(uint32_t now_ms) const;
  void MarkWatered(uint32_t now_ms);
  void PublishEvent(const char* mode, uint8_t port, uint32_t duration_s, uint8_t soil_percent);
  uint32_t BuildDayKey(const Services::TimeFields& fields) const;
  bool DayMatches(uint8_t wday, uint8_t mask) const;

  Services::MqttService* mqtt_ = nullptr;
  Services::TimeService* time_ = nullptr;
  Modules::ActuatorModule* actuator_ = nullptr;
  Modules::ConfigSyncModule* config_sync_ = nullptr;
  Modules::SensorHubModule* sensor_hub_ = nullptr;
  const char* device_id_ = nullptr;

  Util::ScenariosConfig config_{};
  uint32_t last_auto_water_ms_ = 0;
  uint64_t last_event_ms_ = 0;
  uint32_t schedule_fired_[Util::kMaxWaterScheduleEntries]{};
  bool last_light_on_ = false;
};

}
