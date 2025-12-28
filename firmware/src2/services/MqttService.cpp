#include "services/MqttService.h"
#include "util/Logger.h"

namespace Services {

void MqttService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init MqttService");
}

}
