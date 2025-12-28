#include "services/WebConfigService.h"
#include "util/Logger.h"

namespace Services {

void WebConfigService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init WebConfigService");
}

}
