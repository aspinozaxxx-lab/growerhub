#pragma once

#include <cstddef>
#include <cstdint>

namespace Services {

class ChipIdProvider {
 public:
  virtual ~ChipIdProvider() = default;
  virtual bool ReadMac(uint8_t mac[6]) = 0;
};

class DeviceIdentity {
 public:
  DeviceIdentity() = default;
  bool Init(ChipIdProvider& provider);
  const char* GetDeviceId() const;
  static bool BuildDeviceId(const uint8_t mac[6], char* out, size_t out_size);

 private:
  char device_id_[16] = {};
};

#if defined(ARDUINO)
class EfuseMacProvider : public ChipIdProvider {
 public:
  bool ReadMac(uint8_t mac[6]) override;
};
#endif

}
