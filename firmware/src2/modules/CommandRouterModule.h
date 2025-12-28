#pragma once

#include <cstdint>
#include "core/Module.h"

namespace Services {
class MqttService;
}

namespace Modules {
class ActuatorModule;
class StateModule;
}

namespace Config {
struct HardwareProfile;
}

namespace Modules {

class CommandRouterModule : public Core::Module {
 public:
  class Rebooter {
   public:
    virtual ~Rebooter() {}
    virtual void Restart() = 0;
  };

  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  void SetRebooter(Rebooter* rebooter);

 private:
  void HandleCommand(const char* topic, const char* payload);
  void SendAckStatus(const char* correlation_id, const char* status, bool accepted);
  void SendAckError(const char* correlation_id, const char* reason);
  void RebootIfSafe(const char* correlation_id);

  Services::MqttService* mqtt_ = nullptr;
  Modules::ActuatorModule* actuator_ = nullptr;
  Modules::StateModule* state_ = nullptr;
  const Config::HardwareProfile* hardware_ = nullptr;
  Rebooter* rebooter_ = nullptr;

#if defined(ARDUINO)
  class EspRebooter : public Rebooter {
   public:
    void Restart() override;
  };

  EspRebooter default_rebooter_;
#endif
};

}
