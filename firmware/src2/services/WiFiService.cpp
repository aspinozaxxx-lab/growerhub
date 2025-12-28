#include "services/WiFiService.h"
#include "util/Logger.h"

namespace Services {

void WiFiService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init WiFiService");
}

}
