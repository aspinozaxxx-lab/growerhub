#include "drivers/dht/Dht22Sensor.h"

namespace Drivers {

void Dht22Sensor::Init(uint8_t pin) {
  pin_ = pin;
}

bool Dht22Sensor::Read(float* out_temp_c, float* out_humidity) {
  if (out_temp_c == nullptr || out_humidity == nullptr) {
    return false;
  }
  *out_temp_c = 0.0f;
  *out_humidity = 0.0f;
  return true;
}

}
