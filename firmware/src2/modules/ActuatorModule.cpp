#include "modules/ActuatorModule.h"
#include "util/Logger.h"

namespace Modules {

void ActuatorModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init ActuatorModule");
}

void ActuatorModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void ActuatorModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
