/*
 * Chto v faile: obyavleniya zagotovki drivera RTC DS3231.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>
#include <ctime>

namespace Drivers {

class Ds3231Driver {
 public:
  /**
   * Init drivera RTC DS3231.
   */
  bool Init();
  /**
   * Pishet UTC epoch vremya v RTC.
   * @param utc_epoch Vremya v sekundah (UTC).
   */
  bool WriteEpoch(std::time_t utc_epoch);
  /**
   * Chitaet epoch vremya iz RTC.
   * @param out_epoch Vyhodnoe unix vremya v sekundah.
   */
  bool ReadEpoch(uint32_t* out_epoch) const;

#if defined(UNIT_TEST)
  /**
   * Testovyi pomoshchnik: BCD encode.
   */
  static bool EncodeBcdForTests(int value, uint8_t* out);
  /**
   * Testovyi pomoshchnik: BCD decode.
   */
  static bool DecodeBcdForTests(uint8_t value, uint8_t* out);
  /**
   * Testovyi pomoshchnik: den nedeli (1..7).
   */
  static uint8_t CalcWeekdayForTests(std::time_t utc_epoch);
#endif

 private:
  // Flag uspeshnoi inicializacii RTC.
  bool ready_ = false;
};

}
