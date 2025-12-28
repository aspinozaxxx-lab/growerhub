#pragma once

#include <cstddef>
#include <cstdint>

namespace Drivers {

class Rj9PortScanner {
 public:
  static const size_t kMaxPorts = 2;
  using AdcReader = uint16_t (*)(uint8_t pin);

  void Init(const uint8_t* ports, size_t count);
  void SetAdcReader(AdcReader reader);
  void SetCalibration(uint16_t dry_value, uint16_t wet_value);

  void Scan();
  bool IsDetected(uint8_t port) const;
  uint16_t GetLastRaw(uint8_t port) const;
  uint8_t GetLastPercent(uint8_t port) const;
  size_t GetPortCount() const;

 private:
  uint16_t ReadAdc(uint8_t pin) const;
  uint8_t ConvertToPercent(uint16_t raw) const;

  uint8_t ports_[kMaxPorts]{};
  size_t port_count_ = 0;
  bool detected_[kMaxPorts]{};
  uint16_t last_raw_[kMaxPorts]{};
  uint8_t last_percent_[kMaxPorts]{};
  uint8_t good_count_[kMaxPorts]{};
  uint8_t bad_count_[kMaxPorts]{};
  uint16_t dry_value_ = 4095;
  uint16_t wet_value_ = 1800;
  AdcReader adc_reader_ = nullptr;
};

}
