/*
 * Chto v faile: obyavleniya struktury konteksta s ukazatelyami na servisy i moduli.
 * Rol v arhitekture: core.
 * Naznachenie: publichnyi API i tipy dlya sloya core.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

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
  // Ukazatel na planirivshchik zadach.
  Scheduler* scheduler;
  // Ukazatel na ochered sobytiy.
  EventQueue* event_queue;
  // Ukazatel na MQTT servis.
  Services::MqttService* mqtt;
  // Ukazatel na servis hranilishcha.
  Services::StorageService* storage;
  // Ukazatel na servis vremeni.
  Services::TimeService* time;
  // Ukazatel na modul aktuatorov.
  Modules::ActuatorModule* actuator;
  // Ukazatel na modul sinhronizacii konfiguracii.
  Modules::ConfigSyncModule* config_sync;
  // Ukazatel na modul datchikov.
  Modules::SensorHubModule* sensor_hub;
  // Ukazatel na modul sostoyaniya.
  Modules::StateModule* state;
  // Ukazatel na profil zheleza.
  const Config::HardwareProfile* hardware;
  // Identifikator ustroistva.
  const char* device_id;
};

}
