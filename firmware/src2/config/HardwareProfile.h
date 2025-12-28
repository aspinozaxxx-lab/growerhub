#pragma once

#include <cstdint>
#include "config/PinMap.h"

namespace Config {

struct HardwareProfile {
  const char* name;
  const char* device_id;
  PinMap pins;
  uint32_t pump_max_runtime_ms;
};

inline const HardwareProfile& GetHardwareProfile() {
  static const HardwareProfile profile = {
      "default",
      "gh-dev",
      {4, true, 5, true},
      300000};
  return profile;
}

}
