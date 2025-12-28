/*
 * Chto v faile: obyavleniya drivera rele.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

namespace Drivers {

class Relay {
 public:
  /**
   * Init rele s ukazannym pinom.
   * @param pin GPIO pin rele.
   * @param inverted Flag invertirovaniya logiki.
   */
  void Init(uint8_t pin, bool inverted);
  /**
   * Ustanavlivaet sostoyanie rele.
   * @param on True dlya vklyucheniya, false dlya vyklucheniya.
   */
  void Set(bool on);
  /**
   * Vozvrashaet tekuschee sostoyanie rele.
   */
  bool Get() const;

 private:
  uint8_t pin_ = 0;
  bool inverted_ = true;
  bool state_ = false;
};

}
