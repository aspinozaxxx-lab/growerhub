/*
 * Chto v faile: obyavleniya opisaniya profilya zheleza i defoltnogo profilya.
 * Rol v arhitekture: config.
 * Naznachenie: publichnyi API i tipy dlya sloya config.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>
#include "config/PinMap.h"

namespace Config {

struct HardwareProfile {
  // Nazvanie profilya.
  const char* name;
  // Karta pinov dlya etogo profilya.
  PinMap pins;
  // Kolichestvo portov pochvennyh datchikov.
  uint8_t soil_port_count;
  // Flag nalichiya datchika DHT22.
  bool has_dht22; // flag: dht22_vklyuchen_v_profil
  // Flag avto-reboota pri oshibkah DHT22.
  bool dht_auto_reboot_on_fail; // flag: avto_reboot_pri_dht_fail
  // Maksimalnaya dlitelnost raboty nasosa.
  uint32_t pump_max_runtime_ms;
  // Maksimalnaya moshchnost Wi-Fi TX v qdbm (0.25 dBm).
  int wifi_tx_power_qdbm;
};

/**
 * Vozvrashaet defoltnyi profil zheleza.
 */
inline const HardwareProfile& GetHardwareProfile() {
  static const HardwareProfile default_profile = {
      "default",
      {4, false, 5, false, {34, 35}, 15, 21, 22},
      2,
      true,
      true,
      300000,
      0};
#if defined(GH_HW_PROFILE_ESP32C3_SUPERMINI)
  static const HardwareProfile esp32c3_supermini_profile = {
      "esp32c3_supermini",
      {3, false, 5, false, {0, 0}, 1, 4, 5},
      1,
      true,
      false,
      300000,
      60};
  return esp32c3_supermini_profile;
#endif
  return default_profile;
}

}
