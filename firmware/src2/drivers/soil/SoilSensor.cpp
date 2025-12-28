#include "drivers/soil/SoilSensor.h"

namespace Drivers {

void SoilSensor::Init(uint8_t port, Rj9PortScanner* scanner) {
  port_ = port;
  scanner_ = scanner;
}

bool SoilSensor::IsDetected() const {
  if (!scanner_) {
    return false;
  }
  return scanner_->IsDetected(port_);
}

uint16_t SoilSensor::GetRaw() const {
  if (!scanner_) {
    return 0;
  }
  return scanner_->GetLastRaw(port_);
}

uint8_t SoilSensor::GetPercent() const {
  if (!scanner_) {
    return 0;
  }
  return scanner_->GetLastPercent(port_);
}

}
