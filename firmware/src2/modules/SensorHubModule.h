#pragma once

#include "core/Module.h"
#include "drivers/soil/Rj9PortScanner.h"

namespace Modules {

class SensorHubModule : public Core::Module {
 public:
  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  const Drivers::Rj9PortScanner* GetScanner() const;
  Drivers::Rj9PortScanner* GetScanner();

 private:
  static void ScanTask(Core::Context& ctx, uint32_t now_ms);
  void ScanNow(uint32_t now_ms);

  Drivers::Rj9PortScanner scanner_;
  uint32_t last_scan_ms_ = 0;
  bool pump_blocked_ = false;
  bool rescan_pending_ = false;
  uint32_t rescan_at_ms_ = 0;
};

}
