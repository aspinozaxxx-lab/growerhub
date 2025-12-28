/*
 * Chto v faile: realizaciya servisa upravleniya Wi-Fi i AP.
 * Rol v arhitekture: services.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe services.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "services/WiFiService.h"

#include <cctype>
#include <cstdio>
#include <cstring>
#include <string>

#include "core/EventQueue.h"
#include "services/StorageService.h"
#include "services/wifi/BuiltinWifiDefaults.h"
#include "util/JsonUtil.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <WiFi.h>
#endif

namespace Services {

static const uint32_t kStaAttemptIntervalMs = 5000;
static const char* kApSsidPrefix = "Grovika-";
static const char* kApPassword = "grovika123";

static const char* SkipWsLocal(const char* ptr) {
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

static bool ExtractUintField(const char* json, const char* key, uint32_t& out) {
  if (!json || !key) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  if (!key_pos) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon) {
    return false;
  }
  const char* value = SkipWsLocal(colon + 1);
  if (!value || !std::isdigit(static_cast<unsigned char>(*value))) {
    return false;
  }
  uint32_t result = 0;
  while (*value && std::isdigit(static_cast<unsigned char>(*value))) {
    result = result * 10 + static_cast<uint32_t>(*value - '0');
    ++value;
  }
  out = result;
  return true;
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
  const char* value = SkipWsLocal(colon + 1);
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
  event_queue_ = ctx.event_queue;
  device_id_ = ctx.device_id;
  preferred_ = GetPreferredNetworks();
  sta_index_ = 0;
  last_attempt_ms_ = 0;
  last_status_ = -1;
  ap_started_ = false;
  last_attempt_ssid_[0] = '\0';
  Util::Logger::Info("[WIFI] init");

  StartAccessPoint();
  StartStaConnect(0);
}

void WiFiService::Loop(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
#if defined(ARDUINO)
  const int status = static_cast<int>(WiFi.status());
  if (status != last_status_) {
    const char* ssid = last_attempt_ssid_[0] != '\0' ? last_attempt_ssid_ : "unknown";
    const String current_ssid = WiFi.SSID();
    if (current_ssid.length() > 0) {
      ssid = current_ssid.c_str();
    }
    const String ip = WiFi.localIP().toString();
    const int rssi = WiFi.RSSI();
    char log_buf[256];
    if (status == WL_CONNECTED) {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] sta_up ssid=%s ip=%s rssi=%d status=%d",
                    ssid,
                    ip.c_str(),
                    rssi,
                    status);
      Util::Logger::Info(log_buf);
      if (event_queue_) {
        Core::Event event{};
        event.type = Core::EventType::kWifiStaUp;
        event_queue_->Push(event);
      }
    } else if (last_status_ == WL_CONNECTED) {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] sta_down ssid=%s ip=%s rssi=%d status=%d",
                    ssid,
                    ip.c_str(),
                    rssi,
                    status);
      Util::Logger::Info(log_buf);
      if (event_queue_) {
        Core::Event event{};
        event.type = Core::EventType::kWifiStaDown;
        event_queue_->Push(event);
      }
    } else if (status == WL_CONNECT_FAILED || status == WL_NO_SSID_AVAIL || status == WL_DISCONNECTED) {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] sta_error ssid=%s ip=%s rssi=%d status=%d",
                    ssid,
                    ip.c_str(),
                    rssi,
                    status);
      Util::Logger::Info(log_buf);
    } else {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] sta_status ssid=%s ip=%s rssi=%d status=%d",
                    ssid,
                    ip.c_str(),
                    rssi,
                    status);
      Util::Logger::Info(log_buf);
    }
    last_status_ = status;
  }

  if (status == WL_CONNECTED) {
    return;
  }
  if (preferred_.count == 0) {
    return;
  }
  if (now_ms - last_attempt_ms_ < kStaAttemptIntervalMs) {
    return;
  }
  StartStaConnect(now_ms);
#else
  (void)now_ms;
#endif
}

void WiFiService::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  if (event.type != Core::EventType::kWifiConfigUpdated) {
    return;
  }
  preferred_ = GetPreferredNetworks();
  sta_index_ = 0;
  last_attempt_ms_ = 0;
#if defined(ARDUINO)
  WiFi.disconnect(true);
  StartStaConnect(0);
#endif
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
    Util::Logger::Info("[CFG] wifi.json skip: no storage");
    return false;
  }
  if (!storage_->Exists("/cfg/wifi.json")) {
    Util::Logger::Info("[CFG] wifi.json not_found");
    return false;
  }
  Util::Logger::Info("[CFG] wifi.json found");
  char json[1024];
  if (!storage_->ReadFile("/cfg/wifi.json", json, sizeof(json))) {
    Util::Logger::Info("[CFG] wifi.json read_fail");
    return false;
  }
  if (!ParseWifiConfig(json, out)) {
    Util::Logger::Info("[CFG] wifi.json parse_fail");
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
  uint32_t schema_version = 0;
  if (!ExtractUintField(json, "schema_version", schema_version)) {
    return false;
  }
  char log_buf[512];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[CFG] wifi.json schema_version=%u",
                static_cast<unsigned int>(schema_version));
  Util::Logger::Info(log_buf);
  if (schema_version != Util::kWifiSchemaVersion) {
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[CFG] wifi.json schema_mismatch expected=%u",
                  static_cast<unsigned int>(Util::kWifiSchemaVersion));
    Util::Logger::Info(log_buf);
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
  std::string ssids;
  for (size_t i = 0; i < out.count; ++i) {
    if (!ssids.empty()) {
      ssids += ",";
    }
    ssids += out.entries[i].ssid;
  }
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[CFG] wifi.json networks=%u ssids=%s",
                static_cast<unsigned int>(out.count),
                ssids.c_str());
  Util::Logger::Info(log_buf);
  return out.count > 0;
}

void WiFiService::StartStaConnect(uint32_t now_ms) {
  if (preferred_.count == 0) {
    return;
  }
#if defined(ARDUINO)
  const WiFiNetwork& network = preferred_.entries[sta_index_];
  CopyField(network.ssid, last_attempt_ssid_, sizeof(last_attempt_ssid_));
  char log_buf[128];
  std::snprintf(log_buf, sizeof(log_buf), "[WIFI] sta_connect ssid=%s", last_attempt_ssid_);
  Util::Logger::Info(log_buf);
  WiFi.disconnect(true);
  WiFi.begin(network.ssid, network.password);
#endif
  last_attempt_ms_ = now_ms;
  sta_index_ = (sta_index_ + 1) % preferred_.count;
}

void WiFiService::StartAccessPoint() {
#if defined(ARDUINO)
  WiFi.mode(WIFI_AP_STA);

  char ap_ssid[64];
  const char* device_id = device_id_ ? device_id_ : "device";
  std::snprintf(ap_ssid, sizeof(ap_ssid), "%s%s", kApSsidPrefix, device_id);

  ap_started_ = WiFi.softAP(ap_ssid, kApPassword);
  char log_buf[128];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[WIFI] ap_start ssid=%s ok=%s",
                ap_ssid,
                ap_started_ ? "true" : "false");
  Util::Logger::Info(log_buf);
  if (ap_started_ && event_queue_) {
    Core::Event event{};
    event.type = Core::EventType::kWifiApUp;
    event_queue_->Push(event);
  }
#endif
}

}
