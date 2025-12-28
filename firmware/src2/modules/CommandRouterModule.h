/*
 * Chto v faile: obyavleniya modulya marshrutizacii komand i reboot.
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
class StateModule;
}

namespace Modules {

class CommandRouterModule : public Core::Module {
 public:
  class Rebooter {
   public:
    /**
     * Virtualnyi destruktor dlya interfeisa reboota.
     */
    virtual ~Rebooter() {}
    /**
     * Vipolnyaet perezapusk ustroistva.
     */
    virtual void Restart() = 0;
  };

  /**
   * Init modula marshrutizacii komand.
   * @param ctx Kontekst s servisami i modulami.
   */
  void Init(Core::Context& ctx) override;
  /**
   * Obrabotka MQTT sobytiy i reboot zaprosov.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  /**
   * Periodicheskiy tick modula.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  /**
   * Ustanavlivaet obiekt dlya vypolneniya reboot.
   * @param rebooter Ukazatel na realizaciyu reboota.
   */
  void SetRebooter(Rebooter* rebooter);

 private:
  void HandleCommand(const char* topic, const char* payload);
  void SendAckStatus(const char* correlation_id, const char* status, bool accepted);
  void SendAckError(const char* correlation_id, const char* reason);
  void RebootIfSafe(const char* correlation_id);
  void RebootIfSafeInternal(const char* correlation_id, bool send_ack, const char* reason);

  Services::MqttService* mqtt_ = nullptr;
  Modules::ActuatorModule* actuator_ = nullptr;
  Modules::ConfigSyncModule* config_sync_ = nullptr;
  Modules::StateModule* state_ = nullptr;
  const char* device_id_ = nullptr;
  Rebooter* rebooter_ = nullptr;

#if defined(ARDUINO)
  class EspRebooter : public Rebooter {
   public:
    void Restart() override;
  };

  EspRebooter default_rebooter_;
#endif
};

}
