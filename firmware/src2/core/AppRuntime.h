#pragma once

#include <array>
#include <cstddef>

#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Module.h"
#include "core/Scheduler.h"
#include "modules/ActuatorModule.h"
#include "modules/AutomationModule.h"
#include "modules/CommandRouterModule.h"
#include "modules/ConfigSyncModule.h"
#include "modules/OtaModule.h"
#include "modules/SensorHubModule.h"
#include "modules/StateModule.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/TimeService.h"
#include "services/WebConfigService.h"
#include "services/WiFiService.h"

namespace Core {

class AppRuntime {
 public:
  void Init();
  void Tick();

 private:
  Scheduler scheduler_;
  EventQueue event_queue_;
  Context context_;

  Services::StorageService storage_service_;
  Services::TimeService time_service_;
  Services::WiFiService wifi_service_;
  Services::WebConfigService web_config_service_;
  Services::MqttService mqtt_service_;

  Modules::CommandRouterModule command_router_module_;
  Modules::ConfigSyncModule config_sync_module_;
  Modules::SensorHubModule sensor_hub_module_;
  Modules::ActuatorModule actuator_module_;
  Modules::AutomationModule automation_module_;
  Modules::StateModule state_module_;
  Modules::OtaModule ota_module_;

  std::array<Module*, 7> modules_;

  void InitServices();
  void InitModules();
  void DispatchEvent(const Event& event);
  static void HeartbeatTask(Context& ctx, uint32_t now_ms);
};

}
