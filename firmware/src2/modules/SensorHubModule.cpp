#include "modules/SensorHubModule.h"

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"
#include "util/Logger.h"

namespace Modules {

static const uint32_t kScanIntervalMs = 5000;
static const uint32_t kRescanDelayMs = 2000;

void SensorHubModule::Init(Core::Context& ctx) {
  const Config::HardwareProfile& profile = ctx.hardware ? *ctx.hardware : Config::GetHardwareProfile();
  scanner_.Init(profile.pins.soil_adc_pins, profile.soil_port_count);

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
    const uint32_t now_ms = event.value;
    rescan_at_ms_ = now_ms + kRescanDelayMs;
    return;
  }
}

void SensorHubModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  if (!rescan_pending_) {
    return;
  }
  if (now_ms >= rescan_at_ms_) {
    rescan_pending_ = false;
    ScanNow(now_ms);
  }
}

const Drivers::Rj9PortScanner* SensorHubModule::GetScanner() const {
  return &scanner_;
}

Drivers::Rj9PortScanner* SensorHubModule::GetScanner() {
  return &scanner_;
}

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

}
