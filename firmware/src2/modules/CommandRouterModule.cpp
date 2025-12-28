#include "modules/CommandRouterModule.h"
#include "util/Logger.h"

namespace Modules {

void CommandRouterModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init CommandRouterModule");
}

void CommandRouterModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void CommandRouterModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
