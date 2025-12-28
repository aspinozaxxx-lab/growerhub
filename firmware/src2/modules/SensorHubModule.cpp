#include "modules/SensorHubModule.h"
#include "util/Logger.h"

namespace Modules {

void SensorHubModule::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init SensorHubModule");
}

void SensorHubModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void SensorHubModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

}
