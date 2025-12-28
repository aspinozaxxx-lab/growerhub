/*
 * Chto v faile: obyavleniya obertki dlya pochvennogo datchika.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

#include "drivers/soil/Rj9PortScanner.h"

namespace Drivers {

class SoilSensor {
 public:
  /**
   * Init obekt datchika dlya porta.
   * @param port Nomer porta.
   * @param scanner Ukazatel na skaner portov.
   */
  void Init(uint8_t port, Rj9PortScanner* scanner);
  /**
   * Proveryaet nalichie datchika.
   */
  bool IsDetected() const;
  /**
   * Vozvrashaet raw znachenie ADC.
   */
  uint16_t GetRaw() const;
  /**
   * Vozvrashaet procent vlazhnosti.
   */
  uint8_t GetPercent() const;

 private:
  uint8_t port_ = 0;
  Rj9PortScanner* scanner_ = nullptr;
};

}
