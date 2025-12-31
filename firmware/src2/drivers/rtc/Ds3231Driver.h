/*
 * Chto v faile: obyavleniya zagotovki drivera RTC DS3231.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

namespace Drivers {

class Ds3231Driver {
 public:
  /**
   * Init drivera RTC DS3231.
   */
  bool Init();
  /**
   * Chitaet epoch vremya iz RTC.
   * @param out_epoch Vyhodnoe unix vremya v sekundah.
   */
  bool ReadEpoch(uint32_t* out_epoch);

 private:
  // Flag uspeshnoi inicializacii RTC.
  bool ready_ = false;
};

}
