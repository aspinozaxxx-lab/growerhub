#pragma once

#include <cstdint>
#include "core/Module.h"

namespace Services {
class MqttService;
}

namespace Modules {
class ActuatorModule;
class ConfigSyncModule;
}

namespace Config {
struct HardwareProfile;
}

namespace Modules {

class StateModule : public Core::Module {
 public:
  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  void PublishState(bool retained);

 private:
  static const uint32_t kHeartbeatIntervalMs = 20000;

  Services::MqttService* mqtt_ = nullptr;
  Modules::ActuatorModule* actuator_ = nullptr;
  Modules::ConfigSyncModule* config_sync_ = nullptr;
  const Config::HardwareProfile* hardware_ = nullptr;

  uint32_t last_publish_ms_ = 0;
};

}
