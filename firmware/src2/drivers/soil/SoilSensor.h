#pragma once

#include <cstdint>

#include "drivers/soil/Rj9PortScanner.h"

namespace Drivers {

class SoilSensor {
 public:
  void Init(uint8_t port, Rj9PortScanner* scanner);
  bool IsDetected() const;
  uint16_t GetRaw() const;
  uint8_t GetPercent() const;

 private:
  uint8_t port_ = 0;
  Rj9PortScanner* scanner_ = nullptr;
};

}
