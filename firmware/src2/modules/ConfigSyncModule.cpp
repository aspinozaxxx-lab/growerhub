#include "modules/ConfigSyncModule.h"

#include <cstring>

#include "core/Context.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/Topics.h"
#include "util/Logger.h"
#include "util/MqttCodec.h"

namespace Modules {

static const char* kScenariosPath = "/cfg/scenarios.json";

void ConfigSyncModule::Init(Core::Context& ctx) {
  storage_ = ctx.storage;
  mqtt_ = ctx.mqtt;
  event_queue_ = ctx.event_queue;
  device_id_ = ctx.device_id;
  config_ = Util::DefaultScenariosConfig();
  retained_payload_[0] = '\0';
  has_retained_ = false;
  subscribed_ = false;
  sync_requested_ = false;
  mqtt_connected_ = mqtt_ && mqtt_->IsConnected();
  pending_apply_ = !mqtt_connected_;

  LoadFromStorage();
  Util::Logger::Info("init ConfigSyncModule");
}

void ConfigSyncModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  if (event.type != Core::EventType::kMqttMessage) {
    return;
  }

  if (Services::Topics::IsCfgTopic(event.mqtt.topic, device_id_)) {
    if (event.mqtt.payload[0] == '\0') {
      return;
    }
    std::strncpy(retained_payload_, event.mqtt.payload, sizeof(retained_payload_) - 1);
    retained_payload_[sizeof(retained_payload_) - 1] = '\0';
    has_retained_ = true;
    if (mqtt_ && mqtt_->IsConnected()) {
      ApplyRetained();
    } else {
      pending_apply_ = true;
    }
    return;
  }

  if (IsCfgSyncCommand(event.mqtt.topic, event.mqtt.payload)) {
    sync_requested_ = true;
    if (mqtt_ && mqtt_->IsConnected() && has_retained_) {
      ApplyRetained();
    }
  }
}

void ConfigSyncModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;

  const bool connected = mqtt_ && mqtt_->IsConnected();
  if (connected && !subscribed_) {
    char topic[128];
    if (Services::Topics::BuildCfgTopic(topic, sizeof(topic), device_id_)) {
      subscribed_ = mqtt_->Subscribe(topic, 1);
    }
  }

  if (connected && (pending_apply_ || sync_requested_) && has_retained_) {
    ApplyRetained();
  }

  mqtt_connected_ = connected;
}

const Util::ScenariosConfig& ConfigSyncModule::GetConfig() const {
  return config_;
}

void ConfigSyncModule::RequestSync() {
  sync_requested_ = true;
  if (mqtt_ && mqtt_->IsConnected() && has_retained_) {
    ApplyRetained();
  }
}

bool ConfigSyncModule::ApplyRetained() {
  if (!has_retained_) {
    return false;
  }

  Util::ScenariosConfig parsed{};
  if (!Util::DecodeScenariosConfig(retained_payload_, &parsed)) {
    return false;
  }
  if (!Util::ValidateScenariosConfig(parsed)) {
    return false;
  }

  char encoded[1024];
  if (!Util::EncodeScenariosConfig(parsed, encoded, sizeof(encoded))) {
    return false;
  }

  if (storage_ && !storage_->WriteFileAtomic(kScenariosPath, encoded)) {
    return false;
  }

  config_ = parsed;
  pending_apply_ = false;
  sync_requested_ = false;
  EmitConfigUpdated();
  return true;
}

void ConfigSyncModule::LoadFromStorage() {
  if (!storage_) {
    return;
  }
  char payload[1024];
  if (!storage_->ReadFile(kScenariosPath, payload, sizeof(payload))) {
    return;
  }
  Util::ScenariosConfig parsed{};
  if (!Util::DecodeScenariosConfig(payload, &parsed)) {
    return;
  }
  if (!Util::ValidateScenariosConfig(parsed)) {
    return;
  }
  config_ = parsed;
}

void ConfigSyncModule::EmitConfigUpdated() {
  if (!event_queue_) {
    return;
  }
  Core::Event event{};
  event.type = Core::EventType::kConfigUpdated;
  event.value = 0;
  event_queue_->Push(event);
}

bool ConfigSyncModule::IsCfgSyncCommand(const char* topic, const char* payload) const {
  if (!topic || !payload) {
    return false;
  }
  if (!Services::Topics::IsCmdTopic(topic, device_id_)) {
    return false;
  }

  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  if (!Util::ParseCommand(payload, command, error)) {
    return false;
  }
  return command.type == Util::CommandType::kCfgSync;
}

}
