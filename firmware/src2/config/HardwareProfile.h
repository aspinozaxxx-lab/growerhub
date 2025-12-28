#pragma once

#include <cstdint>

namespace Config {

struct HardwareProfile {
  const char* name;
  uint8_t revision;
};

}
