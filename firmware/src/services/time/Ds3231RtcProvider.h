/*
 * Chto v faile: RTC provider dlya DS3231 v TimeService.
 * Rol v arhitekture: services/time.
 * Naznachenie: adapter RTC v vremennyi servis.
 * Soderzhit: klass providera i publichnyi API.
 */

#pragma once

#include <ctime>

#include "drivers/rtc/Ds3231Driver.h"
#include "services/TimeService.h"

namespace Services {

class Ds3231RtcProvider : public IRtcProvider {
 public:
  /**
   * Inicializaciya RTC providera DS3231.
   */
  bool Init();
  /**
   * Vozvrashaet UTC vremya iz RTC.
   * @param out_utc Vyhodnoe UTC vremya.
   */
  bool GetUtc(std::time_t& out_utc) const override;
  /**
   * Popytka zapisi UTC vremeni v RTC.
   * @param utc_epoch UTC epoch v sekundah.
   */
  bool TrySetUtc(std::time_t utc_epoch) override;
  /**
   * Zapis UTC vremeni v RTC.
   * @param utc_epoch UTC epoch v sekundah.
   */
  bool SetUtc(std::time_t utc_epoch);

 private:
  // Driver DS3231 dlya nizkourovnevogo chteniya.
  Drivers::Ds3231Driver driver_;
  // Flag gotovnosti RTC posle init.
  bool ready_ = false;
};

} // namespace Services
