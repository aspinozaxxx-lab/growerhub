#pragma once

namespace Core {
class Scheduler;
class EventQueue;
}

namespace Services {
class MqttService;
class StorageService;
}

namespace Modules {
class ActuatorModule;
class ConfigSyncModule;
class StateModule;
}

namespace Config {
struct HardwareProfile;
}

namespace Core {

struct Context {
  Scheduler* scheduler;
  EventQueue* event_queue;
  Services::MqttService* mqtt;
  Services::StorageService* storage;
  Modules::ActuatorModule* actuator;
  Modules::ConfigSyncModule* config_sync;
  Modules::StateModule* state;
  const Config::HardwareProfile* hardware;
};

}
