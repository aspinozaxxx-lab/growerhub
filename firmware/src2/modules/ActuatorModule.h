#pragma once

#include <cstdint>
#include "core/Module.h"
#include "drivers/relay/Relay.h"

namespace Modules {

struct ManualWateringState {
  bool active;
  uint32_t duration_s;
  const char* started_at;
  const char* correlation_id;
};

class ActuatorModule : public Core::Module {
 public:
  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  bool StartPump(uint32_t duration_s, const char* correlation_id);
  void StopPump(const char* correlation_id);

  bool IsPumpRunning() const;
  bool IsLightOn() const;
  void SetLight(bool on);

  ManualWateringState GetManualWateringState() const;

 private:
  void ResetManualState();
  void StopPumpInternal();

  Drivers::Relay pump_relay_;
  Drivers::Relay light_relay_;

  bool manual_active_ = false;
  uint32_t manual_duration_s_ = 0;
  uint32_t manual_start_ms_ = 0;
  uint32_t max_runtime_ms_ = 0;

  char manual_correlation_id_[64];
  char manual_started_at_[32];
};

}
