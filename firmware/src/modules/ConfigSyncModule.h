/*
 * Chto v faile: obyavleniya modulya sinhronizacii konfiguracii scenariev.
 * Rol v arhitekture: modules.
 * Naznachenie: publichnyi API i tipy dlya sloya modules.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include "core/Module.h"
#include "util/JsonUtil.h"

namespace Services {
class MqttService;
class StorageService;
}

namespace Modules {

class ConfigSyncModule : public Core::Module {
 public:
  /**
   * Init modula sinhronizacii konfiguracii.
   * @param ctx Kontekst s servisami i ocheredyu sobytiy.
   */
  void Init(Core::Context& ctx) override;
  /**
   * Obrabotka MQTT sobytiy konfiguracii.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  /**
   * Periodicheskiy tick modula sinhronizacii.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  /**
   * Vozvrashaet tekushchuyu konfiguraciyu scenariev.
   */
  const Util::ScenariosConfig& GetConfig() const;
  /**
   * Zaprashivaet sinhronizaciyu konfiguracii.
   */
  void RequestSync();

 private:
  bool ApplyRetained();
  void LoadFromStorage();
  void EmitConfigUpdated();
  bool IsCfgSyncCommand(const char* topic, const char* payload) const;

  Services::StorageService* storage_ = nullptr;
  Services::MqttService* mqtt_ = nullptr;
  Core::EventQueue* event_queue_ = nullptr;
  const char* device_id_ = nullptr;

  Util::ScenariosConfig config_{};
  char retained_payload_[1024];
  bool has_retained_ = false;
  bool pending_apply_ = false;
  bool subscribed_ = false;
  bool sync_requested_ = false;
  bool mqtt_connected_ = false;
};

}
