#pragma once

#include <cstdint>

namespace Drivers {

class SoilSensor {
 public:
  void Init(uint8_t port);
  bool Read(uint16_t* out_value);

 private:
  uint8_t port_ = 0;
};

}
