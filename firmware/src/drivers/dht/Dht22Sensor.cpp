/*
 * Chto v faile: realizaciya drivera datchika DHT22.
 * Rol v arhitekture: drivers.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe drivers.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "drivers/dht/Dht22Sensor.h"

#include <cmath>
#include <cstring>

#if defined(ARDUINO)
#include <Arduino.h>
#endif

namespace Drivers {

#if defined(ARDUINO)
namespace {

static bool ReadFrame(uint8_t pin, uint8_t* data, Dht22Sensor::ReadError* out_error) {
  if (!data || !out_error) {
    return false;
  }
  std::memset(data, 0, 5);

  pinMode(pin, OUTPUT);
  digitalWrite(pin, LOW);
  delay(20);
  digitalWrite(pin, HIGH);
  delayMicroseconds(40);
  pinMode(pin, INPUT_PULLUP);

  const unsigned long response_low = pulseIn(pin, LOW, 120);
  const unsigned long response_high = pulseIn(pin, HIGH, 120);
  if (response_low == 0 || response_high == 0) {
    *out_error = Dht22Sensor::ReadError::kNoResponse;
    return false;
  }

  for (uint8_t bit = 0; bit < 40; ++bit) {
    const unsigned long low_len = pulseIn(pin, LOW, 120);
    const unsigned long high_len = pulseIn(pin, HIGH, 120);
    if (low_len == 0 || high_len == 0) {
      *out_error = Dht22Sensor::ReadError::kTimeout;
      return false;
    }
    data[bit / 8] <<= 1;
    if (high_len > 50) {
      data[bit / 8] |= 1;
    }
  }

  const uint8_t checksum = static_cast<uint8_t>(data[0] + data[1] + data[2] + data[3]);
  if (checksum != data[4]) {
    *out_error = Dht22Sensor::ReadError::kChecksum;
    return false;
  }
  return true;
}

}
#endif

void Dht22Sensor::Init(uint8_t pin) {
  pin_ = pin;
  available_ = false;
  last_temp_c_ = 0.0f;
  last_humidity_ = 0.0f;
  last_attempt_ms_ = 0;
  has_read_ = false;
  last_error_ = ReadError::kNone;
#if defined(ARDUINO)
  pinMode(pin_, INPUT_PULLUP);
#endif
}

bool Dht22Sensor::Read(uint32_t now_ms, float* out_temp_c, float* out_humidity) {
  if (!out_temp_c || !out_humidity) {
    return false;
  }
  if (has_read_ && now_ms - last_attempt_ms_ < kMinReadIntervalMs) {
    *out_temp_c = last_temp_c_;
    *out_humidity = last_humidity_;
    return available_;
  }

  last_attempt_ms_ = now_ms;
  has_read_ = true;
  last_error_ = ReadError::kNone;

  float temperature = 0.0f;
  float humidity = 0.0f;
#if defined(UNIT_TEST)
  if (!read_hook_) {
    available_ = false;
    last_error_ = forced_error_ != ReadError::kNone ? forced_error_ : ReadError::kReadFailed;
    return false;
  }
  if (!read_hook_(&temperature, &humidity)) {
    available_ = false;
    last_error_ = forced_error_ != ReadError::kNone ? forced_error_ : ReadError::kReadFailed;
    return false;
  }
#else
  uint8_t data[5];
  if (!ReadFrame(pin_, data, &last_error_)) {
    available_ = false;
    return false;
  }
  const uint16_t raw_humidity = static_cast<uint16_t>((static_cast<uint16_t>(data[0]) << 8) | data[1]);
  const uint16_t raw_temperature = static_cast<uint16_t>((static_cast<uint16_t>(data[2]) << 8) | data[3]);
  humidity = static_cast<float>(raw_humidity) / 10.0f;
  int16_t signed_temperature = static_cast<int16_t>(raw_temperature & 0x7FFF);
  if ((raw_temperature & 0x8000U) != 0) {
    signed_temperature = -signed_temperature;
  }
  temperature = static_cast<float>(signed_temperature) / 10.0f;
#endif

  if (std::isnan(humidity) || std::isnan(temperature)) {
    available_ = false;
    last_error_ = ReadError::kInvalidFrame;
    return false;
  }

  last_temp_c_ = temperature;
  last_humidity_ = humidity;
  available_ = true;
  last_error_ = ReadError::kNone;
  *out_temp_c = last_temp_c_;
  *out_humidity = last_humidity_;
  return true;
}

bool Dht22Sensor::IsAvailable() const {
  return available_;
}

float Dht22Sensor::GetLastTemperature() const {
  return last_temp_c_;
}

float Dht22Sensor::GetLastHumidity() const {
  return last_humidity_;
}

Dht22Sensor::ReadError Dht22Sensor::GetLastError() const {
  return last_error_;
}

const char* Dht22Sensor::GetLastErrorCode() const {
  switch (last_error_) {
    case ReadError::kNoResponse:
      return "no_response";
    case ReadError::kTimeout:
      return "timeout";
    case ReadError::kChecksum:
      return "checksum";
    case ReadError::kInvalidFrame:
      return "invalid_frame";
    case ReadError::kReadFailed:
      return "read_failed";
    case ReadError::kNone:
    default:
      return "none";
  }
}

uint8_t Dht22Sensor::GetPin() const {
  return pin_;
}

#if defined(UNIT_TEST)
void Dht22Sensor::SetReadHook(ReadHook hook) {
  read_hook_ = hook;
}

void Dht22Sensor::SetForcedErrorForTests(ReadError error) {
  forced_error_ = error;
}
#endif

}
