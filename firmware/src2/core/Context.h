#pragma once

namespace Core {
class Scheduler;
class EventQueue;
}

namespace Services {
class MqttService;
}

namespace Modules {
class ActuatorModule;
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
  Modules::ActuatorModule* actuator;
  Modules::StateModule* state;
  const Config::HardwareProfile* hardware;
};

}
