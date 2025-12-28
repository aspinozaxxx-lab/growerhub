/*
 * Chto v faile: realizaciya skanera portov pochvennyh datchikov.
 * Rol v arhitekture: drivers.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe drivers.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "drivers/soil/Rj9PortScanner.h"

#if defined(ARDUINO)
#include <Arduino.h>
#endif

namespace Drivers {

static const size_t kSampleCount = 9;
static const uint16_t kMinRaw = 200;
static const uint16_t kMaxRaw = 4000;
static const uint16_t kMaxSpread = 200;
static const uint8_t kGoodRequired = 3;
static const uint8_t kBadRequired = 3;

static uint16_t DefaultRead(uint8_t pin) {
#if defined(ARDUINO)
  return static_cast<uint16_t>(analogRead(pin));
#else
  (void)pin;
  return 0;
#endif
}

void Rj9PortScanner::Init(const uint8_t* ports, size_t count) {
  port_count_ = count > kMaxPorts ? kMaxPorts : count;
  for (size_t i = 0; i < port_count_; ++i) {
    ports_[i] = ports ? ports[i] : 0;
    detected_[i] = false;
    last_raw_[i] = 0;
    last_percent_[i] = 0;
    good_count_[i] = 0;
    bad_count_[i] = 0;
  }
  for (size_t i = port_count_; i < kMaxPorts; ++i) {
    ports_[i] = 0;
    detected_[i] = false;
    last_raw_[i] = 0;
    last_percent_[i] = 0;
    good_count_[i] = 0;
    bad_count_[i] = 0;
  }
  if (!adc_reader_) {
    adc_reader_ = &DefaultRead;
  }
}

void Rj9PortScanner::SetAdcReader(AdcReader reader) {
  adc_reader_ = reader ? reader : &DefaultRead;
}

void Rj9PortScanner::SetCalibration(uint16_t dry_value, uint16_t wet_value) {
  dry_value_ = dry_value;
  wet_value_ = wet_value;
}

void Rj9PortScanner::Scan() {
  for (size_t port = 0; port < port_count_; ++port) {
    const uint8_t pin = ports_[port];
    uint16_t min_val = 0xFFFF;
    uint16_t max_val = 0;
    uint32_t sum = 0;

    for (size_t i = 0; i < kSampleCount; ++i) {
      const uint16_t sample = ReadAdc(pin);
      if (sample < min_val) {
        min_val = sample;
      }
      if (sample > max_val) {
        max_val = sample;
      }
      sum += sample;
    }

    const uint16_t avg = static_cast<uint16_t>(sum / kSampleCount);
    last_raw_[port] = avg;
    last_percent_[port] = ConvertToPercent(avg);

    const uint16_t spread = max_val >= min_val ? static_cast<uint16_t>(max_val - min_val) : 0;
    const bool in_range = avg >= kMinRaw && avg <= kMaxRaw;
    const bool stable = spread <= kMaxSpread;
    const bool good = in_range && stable;

    if (good) {
      if (good_count_[port] < 255) {
        good_count_[port]++;
      }
      bad_count_[port] = 0;
      if (good_count_[port] >= kGoodRequired) {
        detected_[port] = true;
      }
    } else {
      if (bad_count_[port] < 255) {
        bad_count_[port]++;
      }
      good_count_[port] = 0;
      if (bad_count_[port] >= kBadRequired) {
        detected_[port] = false;
      }
    }
  }
}

bool Rj9PortScanner::IsDetected(uint8_t port) const {
  if (port >= port_count_) {
    return false;
  }
  return detected_[port];
}

uint16_t Rj9PortScanner::GetLastRaw(uint8_t port) const {
  if (port >= port_count_) {
    return 0;
  }
  return last_raw_[port];
}

uint8_t Rj9PortScanner::GetLastPercent(uint8_t port) const {
  if (port >= port_count_) {
    return 0;
  }
  return last_percent_[port];
}

size_t Rj9PortScanner::GetPortCount() const {
  return port_count_;
}

uint16_t Rj9PortScanner::ReadAdc(uint8_t pin) const {
  if (!adc_reader_) {
    return DefaultRead(pin);
  }
  return adc_reader_(pin);
}

uint8_t Rj9PortScanner::ConvertToPercent(uint16_t raw) const {
  if (dry_value_ <= wet_value_) {
    return 0;
  }
  uint16_t clamped = raw;
  if (clamped < wet_value_) {
    clamped = wet_value_;
  }
  if (clamped > dry_value_) {
    clamped = dry_value_;
  }
  const uint32_t numerator = static_cast<uint32_t>(dry_value_ - clamped) * 100U;
  const uint32_t denominator = static_cast<uint32_t>(dry_value_ - wet_value_);
  const uint32_t percent = denominator > 0 ? (numerator / denominator) : 0;
  return percent > 100 ? 100 : static_cast<uint8_t>(percent);
}

}
