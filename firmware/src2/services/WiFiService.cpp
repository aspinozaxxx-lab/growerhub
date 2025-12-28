#include "services/WiFiService.h"

#include <cctype>
#include <cstdio>
#include <cstring>

#include "services/StorageService.h"
#include "services/wifi/BuiltinWifiDefaults.h"
#include "util/Logger.h"

namespace Services {

const char* WiFiService::SkipWs(const char* ptr) {
  const char* current = ptr;
  while (current && *current && std::isspace(static_cast<unsigned char>(*current))) {
    ++current;
  }
  return current;
}

static bool HasJsonBraces(const char* json) {
  if (!json) {
    return false;
  }
  const char* left = std::strchr(json, '{');
  const char* right = std::strrchr(json, '}');
  return left && right && left < right;
}

static void CopyField(const char* src, char* out, size_t out_size) {
  if (!out || out_size == 0) {
    return;
  }
  if (!src) {
    out[0] = '\0';
    return;
  }
  std::strncpy(out, src, out_size - 1);
  out[out_size - 1] = '\0';
}

bool WiFiService::ExtractStringField(const char* start,
                                     const char* limit,
                                     const char* key,
                                     char* out,
                                     size_t out_size) {
  if (!start || !limit || !key || !out || out_size == 0) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(start, pattern);
  if (!key_pos || key_pos >= limit) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon || colon >= limit) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value || value >= limit || *value != '"') {
    return false;
  }
  ++value;
  size_t written = 0;
  while (value < limit && *value && *value != '"' && written + 1 < out_size) {
    out[written++] = *value++;
  }
  if (value >= limit || *value != '"') {
    return false;
  }
  out[written] = '\0';
  return true;
}

void WiFiService::Init(Core::Context& ctx) {
  storage_ = ctx.storage;
  Util::Logger::Info("init WiFiService");
}

WiFiNetworkList WiFiService::GetPreferredNetworks() const {
  WiFiNetworkList list{};
  list.count = 0;
  if (LoadUserNetworks(list)) {
    return list;
  }
  return LoadBuiltinNetworks();
}

bool WiFiService::LoadUserNetworks(WiFiNetworkList& out) const {
  out.count = 0;
  if (!storage_) {
    return false;
  }
  if (!storage_->Exists("/cfg/wifi.json")) {
    return false;
  }
  char json[1024];
  if (!storage_->ReadFile("/cfg/wifi.json", json, sizeof(json))) {
    return false;
  }
  if (!ParseWifiConfig(json, out)) {
    out.count = 0;
    return false;
  }
  return out.count > 0;
}

WiFiNetworkList WiFiService::LoadBuiltinNetworks() {
  WiFiNetworkList list{};
  list.count = 0;
  for (size_t i = 0; i < kBuiltinWifiDefaultsCount && i < kWifiMaxNetworks; ++i) {
    CopyField(kBuiltinWifiDefaults[i].ssid, list.entries[i].ssid, sizeof(list.entries[i].ssid));
    CopyField(kBuiltinWifiDefaults[i].password, list.entries[i].password, sizeof(list.entries[i].password));
    list.count++;
  }
  return list;
}

bool WiFiService::ParseWifiConfig(const char* json, WiFiNetworkList& out) {
  out.count = 0;
  if (!json || !HasJsonBraces(json)) {
    return false;
  }
  const char* networks_key = std::strstr(json, "\"networks\"");
  if (!networks_key) {
    return false;
  }
  const char* array_start = std::strchr(networks_key, '[');
  if (!array_start) {
    return false;
  }
  const char* cursor = array_start + 1;
  while (out.count < kWifiMaxNetworks) {
    const char* obj_start = std::strchr(cursor, '{');
    if (!obj_start) {
      break;
    }
    const char* obj_end = std::strchr(obj_start, '}');
    if (!obj_end) {
      return false;
    }

    WiFiNetwork network{};
    if (!ExtractStringField(obj_start, obj_end, "ssid", network.ssid, sizeof(network.ssid))) {
      cursor = obj_end + 1;
      continue;
    }
    if (!ExtractStringField(obj_start, obj_end, "password", network.password, sizeof(network.password))) {
      network.password[0] = '\0';
    }

    out.entries[out.count++] = network;
    cursor = obj_end + 1;
  }
  return out.count > 0;
}

}
