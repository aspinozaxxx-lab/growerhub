/*
 * Chto v faile: realizaciya modulya publikacii sostoyaniya.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/StateModule.h"

#include <algorithm>
#include <string>

#include "config/BuildFlags.h"
#include "core/Context.h"
#include "modules/ActuatorModule.h"
#include "modules/ConfigSyncModule.h"
#include "modules/SensorHubModule.h"
#include "services/MqttService.h"
#include "services/Topics.h"
#include "util/Logger.h"

// Diagnostika kompilacii: pokazhite znachenie GH_FW_VER v etoy edinice sborki.
#define GH_STRINGIFY_INNER(x) #x
#define GH_STRINGIFY(x) GH_STRINGIFY_INNER(x)
#ifdef GH_FW_VER
#pragma message("GH_FW_VER=" GH_STRINGIFY(GH_FW_VER))
#else
#pragma message("GH_FW_VER not defined")
#endif

namespace Modules {

void StateModule::Init(Core::Context& ctx) {
  mqtt_ = ctx.mqtt;
  actuator_ = ctx.actuator;
  config_sync_ = ctx.config_sync;
  sensor_hub_ = ctx.sensor_hub;
  device_id_ = ctx.device_id;
  last_publish_ms_ = 0;
  Util::Logger::Info("[STATE] init");
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
  if (!mqtt_) {
    Util::Logger::Info("[STATE] publish skip: mqtt null");
    return;
  }
  if (!actuator_) {
    Util::Logger::Info("[STATE] publish skip: actuator null");
    return;
  }
  if (!mqtt_->IsConnected()) {
    Util::Logger::Info("[STATE] publish skip: mqtt disconnected");
    return;
  }

  if (!device_id_) {
    Util::Logger::Info("[STATE] publish skip: device_id null");
    return;
  }
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
  payload += ",\"pump\":{\"status\":\"" + std::string(actuator_->IsPumpRunning() ? "on" : "off") + "\"}";
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
    const size_t port_count = Drivers::Rj9PortScanner::kMaxPorts;
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
  if (!Services::Topics::BuildStateTopic(topic, sizeof(topic), device_id_)) {
    Util::Logger::Info("[STATE] publish skip: bad topic");
    return;
  }

  std::string log_line = "[STATE] publish topic=";
  log_line += topic;
  log_line += " qos=0 retained=";
  log_line += retained ? "true" : "false";
  Util::Logger::Info(log_line.c_str());
  //std::string payload_line = "[STATE] payload_len=";
  //payload_line += std::to_string(payload.size());
  //Util::Logger::Info(payload_line.c_str());

  const size_t chunk_size = 200;
  for (size_t offset = 0; offset < payload.size(); offset += chunk_size) {
    const size_t len = std::min(chunk_size, payload.size() - offset);
    std::string chunk = payload.substr(offset, len);
    std::string chunk_line = "[STATE] payload_chunk=";
    chunk_line += chunk;
    Util::Logger::Info(chunk_line.c_str());
  }

  mqtt_->Publish(topic, payload.c_str(), retained, 0);
}

}

