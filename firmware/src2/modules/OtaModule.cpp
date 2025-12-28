#include "modules/OtaModule.h"
#include "util/Logger.h"

namespace Modules {

void OtaModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init OtaModule");
}

void OtaModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void OtaModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
