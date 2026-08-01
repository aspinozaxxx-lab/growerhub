/*
 * Chto v faile: obyavleniya modulya podtverzhdeniya OTA i rollback.
 * Rol v arhitekture: modules.
 * Naznachenie: publichnyi API i tipy dlya sloya modules.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include "core/Module.h"
#include "services/ota/OtaInstaller.h"
#include "services/ota/OtaRollback.h"

namespace Services {
class MqttService;
}

namespace Modules {
class ActuatorModule;
}

namespace Modules {

class OtaModule : public Core::Module {
 public:
  class Rebooter {
   public:
    virtual ~Rebooter() {}
    virtual void Restart() = 0;
  };

  /**
   * Init modula OTA.
   * @param ctx Kontekst s servisami i hranilishchem.
   */
  void Init(Core::Context& ctx) override;
  /**
   * Obrabotka sobytiy (esli nuzhno).
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  /**
   * Periodicheskiy tick podtverzhdeniya OTA.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;
  /**
   * Pomechaet proshivku kak pending dlya rollback logiki.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void MarkPending(uint32_t now_ms);

  /**
   * Zagruzhaet i ustanavlivaet OTA po proverennoy komande.
   */
  bool StartUpdate(const char* url, const char* version, const char* sha256,
                   const char* correlation_id);

  void SetInstaller(Services::OtaInstaller* installer);
  void SetRebooter(Rebooter* rebooter);

 private:
  void SendAck(const char* correlation_id, const char* result, const char* status,
               const char* version, const char* reason);

  Services::MqttService* mqtt_ = nullptr;
  Modules::ActuatorModule* actuator_ = nullptr;
  const char* device_id_ = nullptr;
  Services::OtaRollback rollback_{};
  Services::PlatformOtaInstaller default_installer_{};
  Services::OtaInstaller* installer_ = nullptr;
  Rebooter* rebooter_ = nullptr;
  bool boot_checked_ = false;

#if defined(ARDUINO)
  class EspRebooter : public Rebooter {
   public:
    void Restart() override;
  };

  EspRebooter default_rebooter_{};
#endif
};

}
