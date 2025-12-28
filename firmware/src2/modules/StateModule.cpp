#include "modules/StateModule.h"
#include "util/Logger.h"

namespace Modules {

void StateModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init StateModule");
}

void StateModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void StateModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
