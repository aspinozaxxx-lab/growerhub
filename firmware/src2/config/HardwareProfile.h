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
};

/**
 * Vozvrashaet defoltnyi profil zheleza.
 */
inline const HardwareProfile& GetHardwareProfile() {
  static const HardwareProfile profile = {
      "default",
      {4, true, 5, true, {34, 35}, 15},
      2,
      false,
      false,
      300000};
  return profile;
}

}
