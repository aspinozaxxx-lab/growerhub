#include "modules/AutomationModule.h"
#include "util/Logger.h"

namespace Modules {

void AutomationModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init AutomationModule");
}

void AutomationModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void AutomationModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
