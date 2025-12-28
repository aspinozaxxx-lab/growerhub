#include "modules/StateModule.h"

#include <string>

#include "config/BuildFlags.h"
#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "modules/ActuatorModule.h"
#include "modules/ConfigSyncModule.h"
#include "modules/SensorHubModule.h"
#include "services/MqttService.h"
#include "services/Topics.h"
#include "util/Logger.h"

namespace Modules {

void StateModule::Init(Core::Context& ctx) {
  mqtt_ = ctx.mqtt;
  actuator_ = ctx.actuator;
  config_sync_ = ctx.config_sync;
  sensor_hub_ = ctx.sensor_hub;
  hardware_ = ctx.hardware;
  last_publish_ms_ = 0;
  Util::Logger::Info("init StateModule");
}

void StateModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void StateModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  if (!mqtt_ || !actuator_ || !mqtt_->IsConnected()) {
    return;
  }

  if (last_publish_ms_ == 0 || now_ms - last_publish_ms_ >= kHeartbeatIntervalMs) {
    PublishState(true);
    last_publish_ms_ = now_ms;
  }
}

void StateModule::PublishState(bool retained) {
  if (!mqtt_ || !actuator_ || !mqtt_->IsConnected()) {
    return;
  }

  const Config::HardwareProfile& profile = hardware_ ? *hardware_ : Config::GetHardwareProfile();
  const ManualWateringState manual = actuator_->GetManualWateringState();
  const char* status = manual.active ? "running" : "idle";
  const bool has_correlation = manual.correlation_id && manual.correlation_id[0] != '\0';
  const bool has_started = manual.started_at && manual.started_at[0] != '\0';

  std::string payload = "{";
  payload += "\"manual_watering\":{";
  payload += "\"status\":\"" + std::string(status) + "\",";
  payload += "\"duration_s\":" + (manual.active ? std::to_string(manual.duration_s) : std::string("null")) + ",";
  payload += "\"started_at\":" + (manual.active && has_started ? "\"" + std::string(manual.started_at) + "\"" : std::string("null")) + ",";
  payload += "\"correlation_id\":" + (manual.active && has_correlation ? "\"" + std::string(manual.correlation_id) + "\"" : std::string("null"));
  payload += "},";
  payload += "\"fw\":\"" + std::string(Config::kFwVersion) + "\"";
  if (Config::kFwInfoAvailable) {
    payload += ",\"fw_ver\":\"" + std::string(Config::kFwVer) + "\"";
    payload += ",\"fw_name\":\"" + std::string(Config::kFwName) + "\"";
    payload += ",\"fw_build\":\"" + std::string(Config::kFwBuild) + "\"";
  }
  payload += ",\"light\":{\"status\":\"" + std::string(actuator_->IsLightOn() ? "on" : "off") + "\"}";

  bool water_time = false;
  bool water_moisture = false;
  bool light_schedule = false;
  if (config_sync_) {
    const Util::ScenariosConfig& cfg = config_sync_->GetConfig();
    water_time = cfg.water_schedule.enabled;
    water_moisture = cfg.water_moisture.enabled;
    light_schedule = cfg.light_schedule.enabled;
  }
  payload += ",\"scenarios\":{";
  payload += "\"water_time\":{\"enabled\":" + std::string(water_time ? "true" : "false") + "},";
  payload += "\"water_moisture\":{\"enabled\":" + std::string(water_moisture ? "true" : "false") + "},";
  payload += "\"light_schedule\":{\"enabled\":" + std::string(light_schedule ? "true" : "false") + "}";
  payload += "}";

  Modules::SensorHubModule::DhtReading dht{};
  const bool has_dht = sensor_hub_ && sensor_hub_->GetDhtReading(&dht);
  const bool dht_available = has_dht && dht.available;
  payload += ",\"air\":{";
  payload += "\"available\":" + std::string(dht_available ? "true" : "false");
  if (dht_available) {
    payload += ",\"temperature\":" + std::to_string(dht.temperature_c);
    payload += ",\"humidity\":" + std::to_string(dht.humidity);
  } else {
    payload += ",\"temperature\":null,\"humidity\":null";
  }
  payload += "}";

  if (sensor_hub_ && sensor_hub_->GetScanner()) {
    const Drivers::Rj9PortScanner* scanner = sensor_hub_->GetScanner();
    const size_t port_count = scanner->GetPortCount();
    payload += ",\"soil\":{\"ports\":[";
    for (size_t i = 0; i < port_count; ++i) {
      const bool detected = scanner->IsDetected(static_cast<uint8_t>(i));
      payload += "{";
      payload += "\"port\":" + std::to_string(static_cast<int>(i)) + ",";
      payload += "\"detected\":" + std::string(detected ? "true" : "false");
      if (detected) {
        payload += ",\"percent\":" + std::to_string(static_cast<int>(scanner->GetLastPercent(static_cast<uint8_t>(i))));
      }
      payload += "}";
      if (i + 1 < port_count) {
        payload += ",";
      }
    }
    payload += "]}";
  }
  payload += "}";

  char topic[128];
  if (!Services::Topics::BuildStateTopic(topic, sizeof(topic), profile.device_id)) {
    return;
  }

  mqtt_->Publish(topic, payload.c_str(), retained, 0);
}

}
