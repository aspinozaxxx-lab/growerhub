#pragma once

#include <cstdint>

#if defined(ARDUINO)
class DHT;
#endif

namespace Drivers {

class Dht22Sensor {
 public:
  void Init(uint8_t pin);
  bool Read(uint32_t now_ms, float* out_temp_c, float* out_humidity);
  bool IsAvailable() const;
  float GetLastTemperature() const;
  float GetLastHumidity() const;

#if defined(UNIT_TEST)
  using ReadHook = bool (*)(float* out_temp_c, float* out_humidity);
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
