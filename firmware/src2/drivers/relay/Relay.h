#pragma once

#include <cstdint>

namespace Drivers {

class Relay {
 public:
  void Init(uint8_t pin);
  void Set(bool on);
  bool Get() const;

 private:
  uint8_t pin_ = 0;
  bool state_ = false;
};

}
