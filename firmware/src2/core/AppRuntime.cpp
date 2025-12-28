/*
 * Chto v faile: realizaciya orkestratora init i tick dlya servisov i moduley.
 * Rol v arhitekture: core.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe core.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "core/AppRuntime.h"

#include "config/HardwareProfile.h"
#include "services/Topics.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#endif

#if !defined(ARDUINO)
namespace {
class ZeroMacProvider : public Services::ChipIdProvider {
 public:
  bool ReadMac(uint8_t mac[6]) override {
    if (!mac) {
      return false;
    }
    for (int i = 0; i < 6; ++i) {
      mac[i] = 0;
    }
    return true;
  }
};
}
#endif

namespace Core {

void AppRuntime::Init() {
  Util::Logger::Init();
  context_.scheduler = &scheduler_;
  context_.event_queue = &event_queue_;
  context_.mqtt = &mqtt_service_;
  context_.storage = &storage_service_;
  context_.time = &time_service_;
  context_.actuator = &actuator_module_;
  context_.config_sync = &config_sync_module_;
  context_.sensor_hub = &sensor_hub_module_;
  context_.state = &state_module_;
  context_.hardware = &Config::GetHardwareProfile();
#if defined(ARDUINO)
  Services::EfuseMacProvider mac_provider;
#else
  ZeroMacProvider mac_provider;
#endif
  device_identity_.Init(mac_provider);
  context_.device_id = device_identity_.GetDeviceId();

  InitServices();
  InitModules();
  scheduler_.AddPeriodic("heartbeat", 60000, &AppRuntime::HeartbeatTask);

  char cmd_topic[128];
  if (context_.device_id && Services::Topics::BuildCmdTopic(cmd_topic, sizeof(cmd_topic), context_.device_id)) {
    mqtt_service_.Subscribe(cmd_topic, 1);
  }
}

void AppRuntime::Tick() {
  mqtt_service_.Loop();

  uint32_t now_ms = 0;
#if defined(ARDUINO)
  now_ms = millis();
#else
  static uint32_t fake_now_ms = 0;
  fake_now_ms += 10;
  now_ms = fake_now_ms;
#endif

  scheduler_.Tick(context_, now_ms);

  wifi_service_.Loop(context_, now_ms);
  web_config_service_.Loop(context_);

  Event event;
  while (event_queue_.Pop(event)) {
    DispatchEvent(event);
  }

  for (size_t i = 0; i < modules_.size(); ++i) {
    if (modules_[i] != nullptr) {
      modules_[i]->OnTick(context_, now_ms);
    }
  }
}

void AppRuntime::InitServices() {
  storage_service_.Init(context_);
  time_service_.Init(context_);
  wifi_service_.Init(context_);
  web_config_service_.Init(context_);
  mqtt_service_.Init(context_);
}

void AppRuntime::InitModules() {
  modules_ = {{&command_router_module_,
               &config_sync_module_,
               &sensor_hub_module_,
               &actuator_module_,
               &automation_module_,
               &state_module_,
               &ota_module_}};

  for (size_t i = 0; i < modules_.size(); ++i) {
    modules_[i]->Init(context_);
  }
}

void AppRuntime::DispatchEvent(const Event& event) {
  wifi_service_.OnEvent(context_, event);
  for (size_t i = 0; i < modules_.size(); ++i) {
    modules_[i]->OnEvent(context_, event);
  }
}

void AppRuntime::HeartbeatTask(Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
  Util::Logger::Info("runtime heartbeat");
}

}
