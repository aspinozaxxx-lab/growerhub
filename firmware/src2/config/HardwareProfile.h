#pragma once

#include <cstdint>
#include "config/PinMap.h"

namespace Config {

struct HardwareProfile {
  const char* name;
  const char* device_id;
  PinMap pins;
  uint8_t soil_port_count;
  uint32_t pump_max_runtime_ms;
};

inline const HardwareProfile& GetHardwareProfile() {
  static const HardwareProfile profile = {
      "default",
      "gh-dev",
      {4, true, 5, true, {34, 35}},
      2,
      300000};
  return profile;
}

}
