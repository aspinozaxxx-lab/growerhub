#pragma once

#include "core/Module.h"
#include "util/JsonUtil.h"

namespace Services {
class MqttService;
class StorageService;
}

namespace Config {
struct HardwareProfile;
}

namespace Modules {

class ConfigSyncModule : public Core::Module {
 public:
  void Init(Core::Context& ctx) override;
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  const Util::ScenariosConfig& GetConfig() const;
  void RequestSync();

 private:
  bool ApplyRetained();
  void LoadFromStorage();
  void EmitConfigUpdated();
  bool IsCfgSyncCommand(const char* topic, const char* payload) const;

  Services::StorageService* storage_ = nullptr;
  Services::MqttService* mqtt_ = nullptr;
  Core::EventQueue* event_queue_ = nullptr;
  const Config::HardwareProfile* hardware_ = nullptr;

  Util::ScenariosConfig config_{};
  char retained_payload_[1024];
  bool has_retained_ = false;
  bool pending_apply_ = false;
  bool subscribed_ = false;
  bool sync_requested_ = false;
  bool mqtt_connected_ = false;
};

}
