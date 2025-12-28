#include "drivers/soil/SoilSensor.h"

namespace Drivers {

void SoilSensor::Init(uint8_t port) {
  port_ = port;
}

bool SoilSensor::Read(uint16_t* out_value) {
  if (out_value == nullptr) {
    return false;
  }
  *out_value = 0;
  return true;
}

}
