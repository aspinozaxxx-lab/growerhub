#include "drivers/relay/Relay.h"

namespace Drivers {

void Relay::Init(uint8_t pin) {
  pin_ = pin;
  state_ = false;
}

void Relay::Set(bool on) {
  state_ = on;
}

bool Relay::Get() const {
  return state_;
}

}
