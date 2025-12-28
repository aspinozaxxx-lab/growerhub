#include "modules/ConfigSyncModule.h"
#include "util/Logger.h"

namespace Modules {

void ConfigSyncModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init ConfigSyncModule");
}

void ConfigSyncModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void ConfigSyncModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
