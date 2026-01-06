/*
 * Chto v faile: realizaciya RTC providera DS3231.
 * Rol v arhitekture: services/time.
 * Naznachenie: dostavlyaet UTC epoch iz DS3231.
 * Soderzhit: metody init i chteniya vremeni.
 */

#include "services/time/Ds3231RtcProvider.h"

namespace Services {

// Inicializaciya RTC providera DS3231.
bool Ds3231RtcProvider::Init() {
  ready_ = driver_.Init();
  return ready_;
}

// Vozvrat UTC vremeni iz DS3231.
bool Ds3231RtcProvider::GetUtc(std::time_t& out_utc) const {
  if (!ready_) {
    return false;
  }
  uint32_t epoch = 0;
  if (!driver_.ReadEpoch(&epoch)) {
    return false;
  }
  out_utc = static_cast<std::time_t>(epoch);
  return true;
}

// Popytka zapisi UTC vremeni v DS3231.
bool Ds3231RtcProvider::TrySetUtc(std::time_t utc_epoch) {
  return SetUtc(utc_epoch);
}

// Zapis UTC vremeni v DS3231.
bool Ds3231RtcProvider::SetUtc(std::time_t utc_epoch) {
  if (!ready_) {
    return false;
  }
  return driver_.WriteEpoch(utc_epoch);
}

} // namespace Services
