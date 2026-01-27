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

#include "config/HardwareProfile.h"
#include "core/EventQueue.h"
#include "services/StorageService.h"
#include "services/wifi/BuiltinWifiDefaults.h"
#include "util/JsonUtil.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <WiFi.h>
#if defined(ESP32)
#include <esp_heap_caps.h>
#endif
#if defined(GH_HW_PROFILE_ESP32C3_SUPERMINI)
#include "esp_wifi.h"
#include "esp_wifi_types.h"
#endif
#endif

namespace Services {

static const uint32_t kStaAttemptIntervalMs = 5000;
static const char* kApSsidPrefix = "Grovika-";
static const char* kApPassword = "grovika123";

#if defined(ARDUINO) && defined(GH_HW_PROFILE_ESP32C3_SUPERMINI)
static const char* WifiReasonName(uint8_t reason) {
  switch (reason) {
    case WIFI_REASON_UNSPECIFIED:
      return "UNSPECIFIED";
    case WIFI_REASON_AUTH_EXPIRE:
      return "AUTH_EXPIRE";
    case WIFI_REASON_AUTH_LEAVE:
      return "AUTH_LEAVE";
    case WIFI_REASON_ASSOC_EXPIRE:
      return "ASSOC_EXPIRE";
    case WIFI_REASON_ASSOC_TOOMANY:
      return "ASSOC_TOOMANY";
    case WIFI_REASON_NOT_AUTHED:
      return "NOT_AUTHED";
    case WIFI_REASON_NOT_ASSOCED:
      return "NOT_ASSOCED";
    case WIFI_REASON_ASSOC_LEAVE:
      return "ASSOC_LEAVE";
    case WIFI_REASON_ASSOC_NOT_AUTHED:
      return "ASSOC_NOT_AUTHED";
    case WIFI_REASON_DISASSOC_PWRCAP_BAD:
      return "DISASSOC_PWRCAP_BAD";
    case WIFI_REASON_DISASSOC_SUPCHAN_BAD:
      return "DISASSOC_SUPCHAN_BAD";
    case WIFI_REASON_BSS_TRANSITION_DISASSOC:
      return "BSS_TRANSITION_DISASSOC";
    case WIFI_REASON_IE_INVALID:
      return "IE_INVALID";
    case WIFI_REASON_MIC_FAILURE:
      return "MIC_FAILURE";
    case WIFI_REASON_4WAY_HANDSHAKE_TIMEOUT:
      return "4WAY_HANDSHAKE_TIMEOUT";
    case WIFI_REASON_GROUP_KEY_UPDATE_TIMEOUT:
      return "GROUP_KEY_UPDATE_TIMEOUT";
    case WIFI_REASON_IE_IN_4WAY_DIFFERS:
      return "IE_IN_4WAY_DIFFERS";
    case WIFI_REASON_GROUP_CIPHER_INVALID:
      return "GROUP_CIPHER_INVALID";
    case WIFI_REASON_PAIRWISE_CIPHER_INVALID:
      return "PAIRWISE_CIPHER_INVALID";
    case WIFI_REASON_AKMP_INVALID:
      return "AKMP_INVALID";
    case WIFI_REASON_UNSUPP_RSN_IE_VERSION:
      return "UNSUPP_RSN_IE_VERSION";
    case WIFI_REASON_INVALID_RSN_IE_CAP:
      return "INVALID_RSN_IE_CAP";
    case WIFI_REASON_802_1X_AUTH_FAILED:
      return "802_1X_AUTH_FAILED";
    case WIFI_REASON_CIPHER_SUITE_REJECTED:
      return "CIPHER_SUITE_REJECTED";
    case WIFI_REASON_TDLS_PEER_UNREACHABLE:
      return "TDLS_PEER_UNREACHABLE";
    case WIFI_REASON_TDLS_UNSPECIFIED:
      return "TDLS_UNSPECIFIED";
    case WIFI_REASON_SSP_REQUESTED_DISASSOC:
      return "SSP_REQUESTED_DISASSOC";
    case WIFI_REASON_NO_SSP_ROAMING_AGREEMENT:
      return "NO_SSP_ROAMING_AGREEMENT";
    case WIFI_REASON_BAD_CIPHER_OR_AKM:
      return "BAD_CIPHER_OR_AKM";
    case WIFI_REASON_NOT_AUTHORIZED_THIS_LOCATION:
      return "NOT_AUTHORIZED_THIS_LOCATION";
    case WIFI_REASON_SERVICE_CHANGE_PERCLUDES_TS:
      return "SERVICE_CHANGE_PERCLUDES_TS";
    case WIFI_REASON_UNSPECIFIED_QOS:
      return "UNSPECIFIED_QOS";
    case WIFI_REASON_NOT_ENOUGH_BANDWIDTH:
      return "NOT_ENOUGH_BANDWIDTH";
    case WIFI_REASON_MISSING_ACKS:
      return "MISSING_ACKS";
    case WIFI_REASON_EXCEEDED_TXOP:
      return "EXCEEDED_TXOP";
    case WIFI_REASON_STA_LEAVING:
      return "STA_LEAVING";
    case WIFI_REASON_END_BA:
      return "END_BA";
    case WIFI_REASON_UNKNOWN_BA:
      return "UNKNOWN_BA";
    case WIFI_REASON_TIMEOUT:
      return "TIMEOUT";
    case WIFI_REASON_PEER_INITIATED:
      return "PEER_INITIATED";
    case WIFI_REASON_AP_INITIATED:
      return "AP_INITIATED";
    case WIFI_REASON_INVALID_FT_ACTION_FRAME_COUNT:
      return "INVALID_FT_ACTION_FRAME_COUNT";
    case WIFI_REASON_INVALID_PMKID:
      return "INVALID_PMKID";
    case WIFI_REASON_INVALID_MDE:
      return "INVALID_MDE";
    case WIFI_REASON_INVALID_FTE:
      return "INVALID_FTE";
    case WIFI_REASON_TRANSMISSION_LINK_ESTABLISH_FAILED:
      return "TRANSMISSION_LINK_ESTABLISH_FAILED";
    case WIFI_REASON_ALTERATIVE_CHANNEL_OCCUPIED:
      return "ALTERATIVE_CHANNEL_OCCUPIED";
    case WIFI_REASON_BEACON_TIMEOUT:
      return "BEACON_TIMEOUT";
    case WIFI_REASON_NO_AP_FOUND:
      return "NO_AP_FOUND";
    case WIFI_REASON_AUTH_FAIL:
      return "AUTH_FAIL";
    case WIFI_REASON_ASSOC_FAIL:
      return "ASSOC_FAIL";
    case WIFI_REASON_HANDSHAKE_TIMEOUT:
      return "HANDSHAKE_TIMEOUT";
    case WIFI_REASON_CONNECTION_FAIL:
      return "CONNECTION_FAIL";
    case WIFI_REASON_AP_TSF_RESET:
      return "AP_TSF_RESET";
    case WIFI_REASON_ROAMING:
      return "ROAMING";
    case WIFI_REASON_ASSOC_COMEBACK_TIME_TOO_LONG:
      return "ASSOC_COMEBACK_TIME_TOO_LONG";
    case WIFI_REASON_SA_QUERY_TIMEOUT:
      return "SA_QUERY_TIMEOUT";
    default:
      return "UNKNOWN";
  }
}

static void WiFiLogEvent(WiFiEvent_t event, WiFiEventInfo_t info) {
  switch (event) {
    case ARDUINO_EVENT_WIFI_STA_CONNECTED:
      Util::Logger::Info("[WIFI] sta_connected");
      break;
    case ARDUINO_EVENT_WIFI_STA_GOT_IP: {
      const String ip = WiFi.localIP().toString();
      const int rssi = WiFi.RSSI();
      char log_buf[128];
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] sta_got_ip ip=%s rssi=%d",
                    ip.c_str(),
                    rssi);
      Util::Logger::Info(log_buf);
      break;
    }
    case ARDUINO_EVENT_WIFI_STA_DISCONNECTED: {
      char log_buf[128];
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] sta_disconnected reason=%s(%u)",
                    WifiReasonName(info.wifi_sta_disconnected.reason),
                    static_cast<unsigned int>(info.wifi_sta_disconnected.reason));
      Util::Logger::Info(log_buf);
      break;
    }
    default:
      break;
  }
}
#endif

#if defined(ARDUINO)
// Log heap pered popytkoy STA connect dlya diagnostiki pamyati.
static void LogHeap(const char* tag) {
#if defined(GH_DEBUG_WEB_HEAP)
  if (!tag) {
    return;
  }
  const uint32_t free_heap = ESP.getFreeHeap();
#if defined(ESP32)
  const uint32_t min_heap = ESP.getMinFreeHeap();
  const size_t largest = heap_caps_get_largest_free_block(MALLOC_CAP_8BIT);
  char log_buf[160];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[HEAP] %s free=%lu min=%lu largest=%u",
                tag,
                static_cast<unsigned long>(free_heap),
                static_cast<unsigned long>(min_heap),
                static_cast<unsigned int>(largest));
#else
  char log_buf[128];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[HEAP] %s free=%lu",
                tag,
                static_cast<unsigned long>(free_heap));
#endif
  Util::Logger::Info(log_buf);
#else
  (void)tag;
#endif
}
#endif

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
#if defined(ARDUINO) && defined(GH_HW_PROFILE_ESP32C3_SUPERMINI)
  static bool events_bound = false;
  if (!events_bound) {
    WiFi.onEvent(WiFiLogEvent);
    events_bound = true;
  }
#endif
  Util::Logger::Info("[WIFI] init");

  StartAccessPoint();
#if defined(ARDUINO) && defined(GH_HW_PROFILE_ESP32C3_SUPERMINI)
  // primenyaem tx power do pervogo STA connect
  const Config::HardwareProfile& hw = ctx.hardware ? *ctx.hardware : Config::GetHardwareProfile();
  if (std::strcmp(hw.name, "esp32c3_supermini") == 0 && hw.wifi_tx_power_qdbm > 0) {
    esp_wifi_set_max_tx_power(hw.wifi_tx_power_qdbm);
    const float dbm = static_cast<float>(hw.wifi_tx_power_qdbm) / 4.0f;
    char log_buf[96];
    if (hw.wifi_tx_power_qdbm % 4 == 0) {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] tx_power_qdbm=%d (%.0f dBm)",
                    hw.wifi_tx_power_qdbm,
                    dbm);
    } else if (hw.wifi_tx_power_qdbm % 2 == 0) {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] tx_power_qdbm=%d (%.1f dBm)",
                    hw.wifi_tx_power_qdbm,
                    dbm);
    } else {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "[WIFI] tx_power_qdbm=%d (%.2f dBm)",
                    hw.wifi_tx_power_qdbm,
                    dbm);
    }
    Util::Logger::Info(log_buf);
  }
#endif
  StartStaConnect(0);
}

void WiFiService::Loop(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
#if defined(ARDUINO)
  const int status = static_cast<int>(WiFi.status());
  if (status != last_status_) {
    if (status == WL_CONNECTED) {
      if (event_queue_) {
        Core::Event event{};
        event.type = Core::EventType::kWifiStaUp;
        event_queue_->Push(event);
      }
    } else if (last_status_ == WL_CONNECTED) {
      if (event_queue_) {
        Core::Event event{};
        event.type = Core::EventType::kWifiStaDown;
        event_queue_->Push(event);
      }
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
  last_attempt_ssid_[0] = '\0';
  last_status_ = -1;
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
  char json[2048];
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
  LogHeap("wifi_before_sta_connect");
  if (sta_index_ >= preferred_.count) {
    sta_index_ = 0;
  }
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
                "[WIFI] ap_start ssid=%s",
                ap_ssid);
  Util::Logger::Info(log_buf);
  if (ap_started_ && event_queue_) {
    Core::Event event{};
    event.type = Core::EventType::kWifiApUp;
    event_queue_->Push(event);
  }
#endif
}

}
