#pragma once

#include <cstdint>
#include "config/PinMap.h"

namespace Config {

struct HardwareProfile {
  const char* name;
  PinMap pins;
  uint8_t soil_port_count;
  bool has_dht22; // flag: dht22_vklyuchen_v_profil
  bool dht_auto_reboot_on_fail; // flag: avto_reboot_pri_dht_fail
  uint32_t pump_max_runtime_ms;
};

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
