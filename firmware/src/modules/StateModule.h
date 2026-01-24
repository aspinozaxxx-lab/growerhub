/*
 * Chto v faile: obyavleniya modulya publikacii sostoyaniya.
 * Rol v arhitekture: modules.
 * Naznachenie: publichnyi API i tipy dlya sloya modules.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>
#include "core/Module.h"

namespace Services {
class MqttService;
}

namespace Modules {
class ActuatorModule;
class ConfigSyncModule;
class SensorHubModule;
}

namespace Modules {

class StateModule : public Core::Module {
 public:
  /**
   * Init modula sostoyaniya.
   * @param ctx Kontekst s servisami i modulami.
   */
  void Init(Core::Context& ctx) override;
  /**
   * Obrabotka sobytiy (esli nuzhno).
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  /**
   * Periodicheskiy tick publikacii sostoyaniya.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  /**
   * Publikuet sostoyanie ustroistva v MQTT.
   * @param retained Flag retained dlya soobshcheniya.
   */
  void PublishState(bool retained);

 private:
  static const uint32_t kHeartbeatIntervalMs = 20000;

  Services::MqttService* mqtt_ = nullptr;
  Modules::ActuatorModule* actuator_ = nullptr;
  Modules::ConfigSyncModule* config_sync_ = nullptr;
  Modules::SensorHubModule* sensor_hub_ = nullptr;
  const char* device_id_ = nullptr;

  uint32_t last_publish_ms_ = 0;
};

}
