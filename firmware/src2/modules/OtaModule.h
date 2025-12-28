/*
 * Chto v faile: obyavleniya modulya podtverzhdeniya OTA i rollback.
 * Rol v arhitekture: modules.
 * Naznachenie: publichnyi API i tipy dlya sloya modules.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include "core/Module.h"
#include "services/ota/OtaRollback.h"

namespace Services {
class MqttService;
}

namespace Modules {

class OtaModule : public Core::Module {
 public:
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

 private:
  static const uint32_t kConfirmDelayMs = 30000;

  Services::MqttService* mqtt_ = nullptr;
  Services::OtaRollback rollback_{};
  bool boot_checked_ = false;
  uint32_t boot_ms_ = 0;
};

}
