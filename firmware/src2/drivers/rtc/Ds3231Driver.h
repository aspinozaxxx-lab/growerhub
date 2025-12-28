#pragma once

#include <cstdint>

namespace Drivers {

class Ds3231Driver {
 public:
  void Init();
  bool ReadEpoch(uint32_t* out_epoch);
};

}
