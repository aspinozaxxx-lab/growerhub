#pragma once

#include <cstdint>

namespace Drivers {

class Relay {
 public:
  void Init(uint8_t pin, bool inverted);
  void Set(bool on);
  bool Get() const;

 private:
  uint8_t pin_ = 0;
  bool inverted_ = true;
  bool state_ = false;
};

}
