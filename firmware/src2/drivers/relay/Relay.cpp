#include "drivers/relay/Relay.h"

#if defined(ARDUINO)
#include <Arduino.h>
#endif

namespace Drivers {

void Relay::Init(uint8_t pin, bool inverted) {
  pin_ = pin;
  inverted_ = inverted;
#if defined(ARDUINO)
  pinMode(pin_, OUTPUT);
#endif
  Set(false);
}

void Relay::Set(bool on) {
  state_ = on;
#if defined(ARDUINO)
  const bool pin_state = inverted_ ? !on : on;
  digitalWrite(pin_, pin_state ? HIGH : LOW);
#endif
}

bool Relay::Get() const {
  return state_;
}

}
