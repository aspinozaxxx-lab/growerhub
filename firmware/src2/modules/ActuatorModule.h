/*
 * Chto v faile: obyavleniya modulya upravleniya nasosom i svetom.
 * Rol v arhitekture: modules.
 * Naznachenie: publichnyi API i tipy dlya sloya modules.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>
#include "core/Module.h"
#include "drivers/relay/Relay.h"

namespace Modules {

struct ManualWateringState {
  // Aktivnost ruchnogo poliva.
  bool active;
  // Zaproshennaya dlitelnost poliva.
  uint32_t duration_s;
  // Vremya starta v ISO formate stroki.
  const char* started_at;
  // Correlation ID komandy.
  const char* correlation_id;
};

class ActuatorModule : public Core::Module {
 public:
  /**
   * Init modula aktuatorov.
   * @param ctx Kontekst s servisami i ocheredyu sobytiy.
   */
  void Init(Core::Context& ctx) override;
  /**
   * Obrabotka sobytiy, svyazannyh s aktuatorami.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  /**
   * Periodicheskiy tick logiki aktuatorov.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  /**
   * Zapuskaet nasos na ukazannuyu dlitelnost.
   * @param duration_s Dlitelnost poliva v sekundah.
   * @param correlation_id Correlation ID komandy.
   */
  bool StartPump(uint32_t duration_s, const char* correlation_id);
  /**
   * Ostanavlivaet nasos.
   * @param correlation_id Correlation ID komandy.
   */
  void StopPump(const char* correlation_id);

  /**
   * Proveryaet, rabotaet li nasos.
   */
  bool IsPumpRunning() const;
  /**
   * Proveryaet, vklyuchen li svet.
   */
  bool IsLightOn() const;
  /**
   * Ustanavlivaet sostoyanie sveta.
   * @param on True dlya vklyucheniya, false dlya vyklucheniya.
   */
  void SetLight(bool on);

  /**
   * Vozvrashaet sostoyanie ruchnogo poliva.
   */
  ManualWateringState GetManualWateringState() const;

 private:
  void ResetManualState();
  void StopPumpInternal();
  static uint32_t GetNowMs();

  Drivers::Relay pump_relay_;
  Drivers::Relay light_relay_;
  Core::EventQueue* event_queue_ = nullptr;

  bool manual_active_ = false;
  uint32_t manual_duration_s_ = 0;
  uint32_t manual_start_ms_ = 0;
  uint32_t max_runtime_ms_ = 0;

  char manual_correlation_id_[64];
  char manual_started_at_[32];
};

}
