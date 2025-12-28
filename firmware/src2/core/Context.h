#pragma once

namespace Core {
class Scheduler;
class EventQueue;
}

namespace Services {
class MqttService;
class StorageService;
class TimeService;
}

namespace Modules {
class ActuatorModule;
class ConfigSyncModule;
class SensorHubModule;
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
  Services::TimeService* time;
  Modules::ActuatorModule* actuator;
  Modules::ConfigSyncModule* config_sync;
  Modules::SensorHubModule* sensor_hub;
  Modules::StateModule* state;
  const Config::HardwareProfile* hardware;
  const char* device_id;
};

}
