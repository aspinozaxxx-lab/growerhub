#pragma once

#include <cstdint>

namespace Config {

struct PinMap {
  uint8_t pump_relay_pin;
  bool pump_relay_inverted;
  uint8_t light_relay_pin;
  bool light_relay_inverted;
  uint8_t soil_adc_pins[2];
  uint8_t dht_pin;
};

}
