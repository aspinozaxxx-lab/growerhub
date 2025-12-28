#include "services/TimeService.h"
#include "util/Logger.h"

namespace Services {

void TimeService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init TimeService");
}

}
