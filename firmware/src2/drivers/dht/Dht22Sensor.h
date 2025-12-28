#pragma once

#include <cstdint>

namespace Drivers {

class Dht22Sensor {
 public:
  void Init(uint8_t pin);
  bool Read(float* out_temp_c, float* out_humidity);

 private:
  uint8_t pin_ = 0;
};

}
