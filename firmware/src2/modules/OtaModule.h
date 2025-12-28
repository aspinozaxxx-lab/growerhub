#pragma once

#include "core/Module.h"
#include "services/ota/OtaRollback.h"

namespace Services {
class MqttService;
}

namespace Modules {

class OtaModule : public Core::Module {
 public:
  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;
  void MarkPending(uint32_t now_ms);

 private:
  static const uint32_t kConfirmDelayMs = 30000;

  Services::MqttService* mqtt_ = nullptr;
  Services::OtaRollback rollback_{};
  bool boot_checked_ = false;
  uint32_t boot_ms_ = 0;
};

}
