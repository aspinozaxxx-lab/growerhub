/*
 * Chto v faile: obyavleniya bazovogo interfeisa modula.
 * Rol v arhitekture: core.
 * Naznachenie: publichnyi API i tipy dlya sloya core.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>
#include "core/EventQueue.h"

namespace Core {

struct Context;

class Module {
 public:
  /**
   * Virtualnyi destruktor dlya bazovogo interfeisa.
   */
  virtual ~Module() {}
  /**
   * Init modula posle sozdaniya runtime.
   * @param ctx Kontekst s ukazatelyami na servisy i ochered sobytiy.
   */
  virtual void Init(Context& ctx) = 0;
  /**
   * Obrabotka sobytiya, prishedshego iz ocheredi.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  virtual void OnEvent(Context& ctx, const Event& event) = 0;
  /**
   * Periodicheskiy tick logiki modula.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  virtual void OnTick(Context& ctx, uint32_t now_ms) = 0;
};

}
