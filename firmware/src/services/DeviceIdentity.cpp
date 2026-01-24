/*
 * Chto v faile: realizaciya formirovaniya device_id po MAC.
 * Rol v arhitekture: services.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe services.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "services/DeviceIdentity.h"

#include <cstdio>

#if defined(ARDUINO)
#include <Arduino.h>
#include <esp_mac.h>
#include <esp_efuse.h>
#endif

namespace Services {

bool DeviceIdentity::Init(ChipIdProvider& provider) {
  uint8_t mac[6] = {};
  const bool ok = provider.ReadMac(mac);
  if (!BuildDeviceId(mac, device_id_, sizeof(device_id_))) {
    device_id_[0] = '\0';
    return false;
  }
  return ok;
}

const char* DeviceIdentity::GetDeviceId() const {
  return device_id_;
}

bool DeviceIdentity::BuildDeviceId(const uint8_t mac[6], char* out, size_t out_size) {
  if (!mac || !out || out_size == 0) {
    return false;
  }
  const int written = std::snprintf(out,
                                    out_size,
                                    "GROVIKA_%02X%02X%02X",
                                    static_cast<unsigned int>(mac[3]),
                                    static_cast<unsigned int>(mac[4]),
                                    static_cast<unsigned int>(mac[5]));
  return written > 0 && static_cast<size_t>(written) < out_size;
}

#if defined(ARDUINO)
bool EfuseMacProvider::ReadMac(uint8_t mac[6]) {
  if (!mac) {
    return false;
  }
  if (esp_efuse_mac_get_default(mac) == ESP_OK) {
    return true;
  }
  const uint64_t efuse = ESP.getEfuseMac();
  for (int i = 0; i < 6; ++i) {
    mac[i] = static_cast<uint8_t>(efuse >> (8 * (5 - i)));
  }
  return true;
}
#endif

}
