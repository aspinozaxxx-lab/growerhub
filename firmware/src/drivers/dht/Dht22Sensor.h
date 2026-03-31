/*
 * Chto v faile: obyavleniya drivera datchika DHT22.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

namespace Drivers {

class Dht22Sensor {
 public:
  enum class ReadError : uint8_t {
    kNone = 0,
    kNoResponse = 1,
    kTimeout = 2,
    kChecksum = 3,
    kInvalidFrame = 4,
    kReadFailed = 5
  };

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
  /**
   * Vozvrashaet kod posledney oshibki chteniya.
   */
  ReadError GetLastError() const;
  /**
   * Vozvrashaet tekstovyi kod posledney oshibki chteniya.
   */
  const char* GetLastErrorCode() const;
  /**
   * Vozvrashaet pin datchika.
   */
  uint8_t GetPin() const;

#if defined(UNIT_TEST)
  // Tip hook dlya podmeny chteniya v testah.
  using ReadHook = bool (*)(float* out_temp_c, float* out_humidity);
  /**
   * Ustanavlivaet hook chteniya dlya testov.
   * @param hook Ukazatel na funkciyu chteniya.
   */
  void SetReadHook(ReadHook hook);
  /**
   * Ustanavlivaet prinuditelnyi kod oshibki dlya testov.
   * @param error Kod oshibki, kotoryi nado vernut pri neudachnom chtenii.
   */
  void SetForcedErrorForTests(ReadError error);
#endif

 private:
  static const uint32_t kMinReadIntervalMs = 2000;

  uint8_t pin_ = 0;
  bool available_ = false;
  float last_temp_c_ = 0.0f;
  float last_humidity_ = 0.0f;
  uint32_t last_attempt_ms_ = 0;
  bool has_read_ = false;
  ReadError last_error_ = ReadError::kNone;

#if defined(UNIT_TEST)
  ReadHook read_hook_ = nullptr;
  ReadError forced_error_ = ReadError::kNone;
#endif
};

}
