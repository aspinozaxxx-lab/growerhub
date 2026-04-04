/*
 * Chto v faile: realizaciya drivera datchika DHT22.
 * Rol v arhitekture: drivers.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe drivers.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "drivers/dht/Dht22Sensor.h"

#include <cmath>

#if defined(ARDUINO)
#include <DHT.h>
#endif

namespace Drivers {

void Dht22Sensor::Init(uint8_t pin) {
  pin_ = pin;
  available_ = false;
  last_temp_c_ = 0.0f;
  last_humidity_ = 0.0f;
  last_attempt_ms_ = 0;
  has_read_ = false;
  last_error_ = ReadError::kNone;
#if defined(ARDUINO)
  if (dht_) {
    delete dht_;
  }
  dht_ = new DHT(pin_, DHT22);
  dht_->begin();
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
  if (!dht_) {
    available_ = false;
    last_error_ = ReadError::kReadFailed;
    return false;
  }
  humidity = dht_->readHumidity();
  temperature = dht_->readTemperature();
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
