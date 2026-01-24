/*
 * Chto v faile: obyavleniya struktury kart pinov dlya profilya zheleza.
 * Rol v arhitekture: config.
 * Naznachenie: publichnyi API i tipy dlya sloya config.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

namespace Config {

struct PinMap {
  // Pin rele nasosa.
  uint8_t pump_relay_pin;
  // Flag invertirovaniya rele nasosa.
  bool pump_relay_inverted;
  // Pin rele sveta.
  uint8_t light_relay_pin;
  // Flag invertirovaniya rele sveta.
  bool light_relay_inverted;
  // Piny ADC dlya pochvennyh datchikov.
  uint8_t soil_adc_pins[2];
  // Pin datchika DHT22.
  uint8_t dht_pin;
};

}
