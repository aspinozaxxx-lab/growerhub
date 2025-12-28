#include "services/StorageService.h"
#include "util/Logger.h"

namespace Services {

void StorageService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init StorageService");
}

}
