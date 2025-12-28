#pragma once

#include <cstddef>

#include "core/Context.h"
#include "core/EventQueue.h"

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
  void Loop(Core::Context& ctx, uint32_t now_ms);
  void OnEvent(Core::Context& ctx, const Core::Event& event);
  WiFiNetworkList GetPreferredNetworks() const;

 private:
  bool LoadUserNetworks(WiFiNetworkList& out) const;
  static WiFiNetworkList LoadBuiltinNetworks();
  static bool ParseWifiConfig(const char* json, WiFiNetworkList& out);
  static bool ExtractStringField(const char* start,
                                 const char* limit,
                                 const char* key,
                                 char* out,
                                 size_t out_size);
  void StartStaConnect(uint32_t now_ms);
  void StartAccessPoint();

  StorageService* storage_ = nullptr;
  Core::EventQueue* event_queue_ = nullptr;
  const Config::HardwareProfile* hardware_ = nullptr;
  WiFiNetworkList preferred_{};
  size_t sta_index_ = 0;
  uint32_t last_attempt_ms_ = 0;
  bool ap_started_ = false;
  int last_status_ = -1;
};

}
