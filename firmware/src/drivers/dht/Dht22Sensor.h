/*
 * Chto v faile: obyavleniya drivera datchika DHT22.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

#if defined(ARDUINO)
class DHT;
#endif

namespace Drivers {

class Dht22Sensor {
 public:
  /**
   * Init datchika na ukazannom pine.
   * @param pin GPIO pin datchika.
   */
  void Init(uint8_t pin);
  /**
   * Chitaet temperaturu i vlazhnost.
   * @param now_ms Tekuschee vremya v millisekundah.
   * @param out_temp_c Vyhodnaya temperatura v C.
   * @param out_humidity Vyhodnaya vlazhnost v procentah.
   */
  bool Read(uint32_t now_ms, float* out_temp_c, float* out_humidity);
  /**
   * Proveryaet, dostupno li poslednee chtenie.
   */
  bool IsAvailable() const;
  /**
   * Vozvrashaet poslednyuyu temperaturu.
   */
  float GetLastTemperature() const;
  /**
   * Vozvrashaet poslednyuyu vlazhnost.
   */
  float GetLastHumidity() const;

#if defined(UNIT_TEST)
  // Tip hook dlya podmeny chteniya v testah.
  using ReadHook = bool (*)(float* out_temp_c, float* out_humidity);
  /**
   * Ustanavlivaet hook chteniya dlya testov.
   * @param hook Ukazatel na funkciyu chteniya.
   */
  void SetReadHook(ReadHook hook);
#endif

 private:
  static const uint32_t kMinReadIntervalMs = 2000;

  uint8_t pin_ = 0;
  bool available_ = false;
  float last_temp_c_ = 0.0f;
  float last_humidity_ = 0.0f;
  uint32_t last_attempt_ms_ = 0;
  bool has_read_ = false;

#if defined(ARDUINO)
  ::DHT* dht_ = nullptr;
#endif

#if defined(UNIT_TEST)
  ReadHook read_hook_ = nullptr;
#endif
};

}
