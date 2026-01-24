#include <cstring>
#include <unity.h>

#include "services/DeviceIdentity.h"

class FixedMacProvider : public Services::ChipIdProvider {
 public:
  explicit FixedMacProvider(const uint8_t mac[6]) {
    std::memcpy(mac_, mac, sizeof(mac_));
  }

  bool ReadMac(uint8_t mac[6]) override {
    if (!mac) {
      return false;
    }
    std::memcpy(mac, mac_, sizeof(mac_));
    return true;
  }

 private:
  uint8_t mac_[6];
};

void test_device_identity_legacy_format() {
  const uint8_t mac[6] = {0x10, 0x20, 0x30, 0x04, 0x0A, 0xB1};
  FixedMacProvider provider(mac);
  Services::DeviceIdentity identity;

  TEST_ASSERT_TRUE(identity.Init(provider));
  TEST_ASSERT_EQUAL_STRING("GROVIKA_040AB1", identity.GetDeviceId());
}
