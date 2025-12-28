#pragma once

#include <cstddef>

#include "core/Context.h"

namespace Services {

static const size_t kWifiMaxNetworks = 10;
static const size_t kWifiSsidMaxLen = 32;
static const size_t kWifiPasswordMaxLen = 64;

struct WiFiNetwork {
  char ssid[kWifiSsidMaxLen + 1];
  char password[kWifiPasswordMaxLen + 1];
};

struct WiFiNetworkList {
  size_t count;
  WiFiNetwork entries[kWifiMaxNetworks];
};

class WiFiService {
 public:
  void Init(Core::Context& ctx);
  WiFiNetworkList GetPreferredNetworks() const;

 private:
  bool LoadUserNetworks(WiFiNetworkList& out) const;
  static WiFiNetworkList LoadBuiltinNetworks();
  static bool ParseWifiConfig(const char* json, WiFiNetworkList& out);
  static const char* SkipWs(const char* ptr);
  static bool ExtractStringField(const char* start,
                                 const char* limit,
                                 const char* key,
                                 char* out,
                                 size_t out_size);

  StorageService* storage_ = nullptr;
};

}
