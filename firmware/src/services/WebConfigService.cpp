/*
 * Chto v faile: realizaciya web-nastroiki Wi-Fi i zapisi konfiguracii.
 * Rol v arhitekture: services.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe services.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "services/WebConfigService.h"

#include <cctype>
#include <cstdio>
#include <cstring>

#include "core/EventQueue.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/WiFiService.h"
#include "services/website/WebsiteContent.h"
#include "util/JsonUtil.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <WebServer.h>
#include <WiFi.h>
#if defined(ESP32)
#include <esp_heap_caps.h>
#endif
#endif

namespace Services {

static const char* kWifiConfigPath = "/cfg/wifi.json";
#if defined(ARDUINO)
// Shablon dlya podstanovki statusa Wi-Fi na servere.
static const char kWifiStatusPlaceholder[] = "{{WIFI_STATUS_LINE}}";
// Shablon dlya podstanovki statusa MQTT na servere.
static const char kMqttStatusPlaceholder[] = "{{MQTT_STATUS_LINE}}";

// Log heap dlya diagnostiki prosadok i fragmentacii pamyati.
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

// Otdaet chast' HTML bez bol'shih vremennyh String.
static void SendContentSpan(WebServer* server, const char* start, size_t len) {
  if (!server || !start || len == 0) {
    return;
  }
  // Razmer kuska dlya bezopasnoy otpravki v WebServer.
  static const size_t kChunkSize = 256;
  char buf[kChunkSize + 1];
  size_t offset = 0;
  while (offset < len) {
    size_t chunk = len - offset;
    if (chunk > kChunkSize) {
      chunk = kChunkSize;
    }
    std::memcpy(buf, start + offset, chunk);
    buf[chunk] = '\0';
    server->sendContent(buf);
    offset += chunk;
  }
}
#endif

static const char* SkipWsLocal(const char* ptr) {
  const char* current = ptr;
  while (current && *current && std::isspace(static_cast<unsigned char>(*current))) {
    ++current;
  }
  return current;
}

static bool ExtractStringField(const char* start,
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

static bool ExtractStringFieldJson(const char* json, const char* key, char* out, size_t out_size) {
  if (!json) {
    return false;
  }
  return ExtractStringField(json, json + std::strlen(json), key, out, out_size);
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

static bool LoadNetworksFromStorage(StorageService* storage,
                                    WiFiNetworkList& out,
                                    char* json_buf,
                                    size_t json_buf_size) {
  out.count = 0;
  if (!storage || !storage->Exists(kWifiConfigPath)) {
    return false;
  }
  if (!storage->ReadFile(kWifiConfigPath, json_buf, json_buf_size)) {
    return false;
  }
  return WiFiService::ParseWifiConfig(json_buf, out);
}

enum class BuildJsonResult {
  kOk = 0,
  kTooLarge = 1,
  kInvalid = 2
};

static size_t CountDigits(uint32_t value) {
  size_t digits = 1;
  while (value >= 10) {
    value /= 10;
    ++digits;
  }
  return digits;
}

static bool CanFitNetworksJson(const WiFiNetworkList& list, size_t out_size) {
  const char* json_prefix = "{\"schema_version\":";
  const char* json_mid = ",\"networks\":[";
  const char* json_suffix = "]}";
  const char* entry_prefix = "{\"ssid\":\"";
  const char* entry_mid = "\",\"password\":\"";
  const char* entry_suffix = "\"}";
  size_t needed = std::strlen(json_prefix) + CountDigits(Util::kWifiSchemaVersion) +
                  std::strlen(json_mid) + std::strlen(json_suffix) + 1;
  for (size_t i = 0; i < list.count; ++i) {
    needed += std::strlen(entry_prefix);
    needed += std::strlen(list.entries[i].ssid);
    needed += std::strlen(entry_mid);
    needed += std::strlen(list.entries[i].password);
    needed += std::strlen(entry_suffix);
    if (i + 1 < list.count) {
      needed += 1;
    }
  }
  return needed <= out_size;
}

static BuildJsonResult BuildNetworksJson(const WiFiNetworkList& list, char* out, size_t out_size) {
  if (!CanFitNetworksJson(list, out_size)) {
    return BuildJsonResult::kTooLarge;
  }
  const char* ssids[kWifiMaxNetworks];
  const char* passwords[kWifiMaxNetworks];
  for (size_t i = 0; i < list.count; ++i) {
    ssids[i] = list.entries[i].ssid;
    passwords[i] = list.entries[i].password;
  }
  if (!Util::EncodeWifiConfig(ssids, passwords, list.count, out, out_size)) {
    return BuildJsonResult::kInvalid;
  }
  return BuildJsonResult::kOk;
}

static bool WriteOkEmpty(char* out, size_t out_size) {
  const int written = std::snprintf(out, out_size, "{\"ok\":true,\"networks\":[]}");
  return written > 0 && static_cast<size_t>(written) < out_size;
}

static bool WriteTooLargeError(char* out, size_t out_size) {
  const int written = std::snprintf(out, out_size, "{\"ok\":false,\"error\":\"response_too_large\"}");
  return written > 0 && static_cast<size_t>(written) < out_size;
}

static bool UpsertNetwork(WiFiNetworkList& list, const char* ssid, const char* password) {
  for (size_t i = 0; i < list.count; ++i) {
    if (std::strcmp(list.entries[i].ssid, ssid) == 0) {
      CopyField(password, list.entries[i].password, sizeof(list.entries[i].password));
      return true;
    }
  }
  if (list.count >= kWifiMaxNetworks) {
    return false;
  }
  CopyField(ssid, list.entries[list.count].ssid, sizeof(list.entries[list.count].ssid));
  CopyField(password, list.entries[list.count].password, sizeof(list.entries[list.count].password));
  list.count++;
  return true;
}

static bool RemoveNetwork(WiFiNetworkList& list, const char* ssid) {
  for (size_t i = 0; i < list.count; ++i) {
    if (std::strcmp(list.entries[i].ssid, ssid) == 0) {
      for (size_t j = i + 1; j < list.count; ++j) {
        list.entries[j - 1] = list.entries[j];
      }
      list.count--;
      return true;
    }
  }
  return false;
}

bool WebConfigService::BuildWifiConfigJson(const char* ssid,
                                           const char* password,
                                           char* out,
                                           size_t out_size) {
  const char* ssids[1] = {ssid};
  const char* passwords[1] = {password};
  return Util::EncodeWifiConfig(ssids, passwords, 1, out, out_size);
}

void WebConfigService::Init(Core::Context& ctx) {
  storage_ = ctx.storage;
  event_queue_ = ctx.event_queue;
  device_id_ = ctx.device_id;
  mqtt_ = ctx.mqtt;
  Util::Logger::Info("[CFG] init WebConfigService");

#if defined(ARDUINO)
  if (!server_) {
    server_ = new WebServer(80);
  }

  server_->on("/", HTTP_GET, [this]() {
    LogHeap("web_before_render");
    const bool sta_connected = WiFi.status() == WL_CONNECTED;
    String ip;
    if (sta_connected) {
      ip = WiFi.localIP().toString();
    }
    String wifi_line = "Wi-Fi: ";
    wifi_line += sta_connected ? "CONNECTED" : "DISCONNECTED";
    wifi_line += " (ip=";
    if (sta_connected) {
      wifi_line += ip;
    } else {
      wifi_line += "-";
    }
    wifi_line += ")";

    bool mqtt_connected = false;
    if (mqtt_) {
      mqtt_connected = mqtt_->IsConnected();
    } else {
      // Esli mqtt_ == nullptr, schitaem MQTT otklyuchennym.
    }
    String mqtt_line = "MQTT: ";
    mqtt_line += mqtt_connected ? "CONNECTED" : "DISCONNECTED";

    const char* html = Website::Html();
    const char* wifi_pos = std::strstr(html, kWifiStatusPlaceholder);
    if (!wifi_pos) {
      server_->send(200, "text/html", html);
      LogHeap("web_after_send");
      return;
    }
    const char* after_wifi = wifi_pos + std::strlen(kWifiStatusPlaceholder);
    const char* mqtt_pos = std::strstr(after_wifi, kMqttStatusPlaceholder);
    if (!mqtt_pos) {
      server_->send(200, "text/html", html);
      LogHeap("web_after_send");
      return;
    }
    const char* after_mqtt = mqtt_pos + std::strlen(kMqttStatusPlaceholder);

    server_->setContentLength(CONTENT_LENGTH_UNKNOWN);
    server_->send(200, "text/html", "");
    SendContentSpan(server_, html, static_cast<size_t>(wifi_pos - html));
    server_->sendContent(wifi_line);
    SendContentSpan(server_, after_wifi, static_cast<size_t>(mqtt_pos - after_wifi));
    server_->sendContent(mqtt_line);
    SendContentSpan(server_, after_mqtt, std::strlen(after_mqtt));
    LogHeap("web_after_send");
  });

  server_->on("/style.css", HTTP_GET, [this]() {
    server_->send(200, "text/css", Website::Css());
  });

  server_->on("/app.js", HTTP_GET, [this]() {
    server_->send(200, "application/javascript", Website::Js());
  });

  server_->on("/api/networks", HTTP_GET, [this]() {
    wifi_list_.count = 0;
    if (LoadNetworksFromStorage(storage_, wifi_list_, wifi_json_buf_, sizeof(wifi_json_buf_)) &&
        wifi_list_.count > 0) {
      const BuildJsonResult result = BuildNetworksJson(wifi_list_, wifi_json_buf_, sizeof(wifi_json_buf_));
      if (result == BuildJsonResult::kTooLarge) {
        if (WriteTooLargeError(wifi_json_buf_, sizeof(wifi_json_buf_))) {
          server_->send(500, "application/json", wifi_json_buf_);
        } else {
          server_->send(500, "text/plain", "response_too_large");
        }
        return;
      }
      if (result != BuildJsonResult::kOk) {
        if (WriteOkEmpty(wifi_json_buf_, sizeof(wifi_json_buf_))) {
          server_->send(200, "application/json", wifi_json_buf_);
        } else {
          server_->send(200, "application/json", "{\"ok\":true,\"networks\":[]}");
        }
        return;
      }
      server_->send(200, "application/json", wifi_json_buf_);
      return;
    }
    if (WriteOkEmpty(wifi_json_buf_, sizeof(wifi_json_buf_))) {
      server_->send(200, "application/json", wifi_json_buf_);
    } else {
      server_->send(200, "application/json", "{\"ok\":true,\"networks\":[]}");
    }
  });

  server_->on("/api/networks", HTTP_POST, [this]() {
    if (!storage_) {
      server_->send(500, "text/plain", "Storage unavailable");
      return;
    }
    const String body = server_->arg("plain");
    char ssid[kWifiSsidMaxLen + 1];
    char password[kWifiPasswordMaxLen + 1];
    if (!ExtractStringFieldJson(body.c_str(), "ssid", ssid, sizeof(ssid))) {
      server_->send(400, "text/plain", "SSID required");
      return;
    }
    if (!ExtractStringFieldJson(body.c_str(), "password", password, sizeof(password))) {
      password[0] = '\0';
    }

    wifi_list_.count = 0;
    LoadNetworksFromStorage(storage_, wifi_list_, wifi_json_buf_, sizeof(wifi_json_buf_));
    if (!UpsertNetwork(wifi_list_, ssid, password)) {
      server_->send(400, "text/plain", "Too many networks");
      return;
    }

    const BuildJsonResult result = BuildNetworksJson(wifi_list_, wifi_json_buf_, sizeof(wifi_json_buf_));
    if (result == BuildJsonResult::kTooLarge) {
      if (WriteTooLargeError(wifi_json_buf_, sizeof(wifi_json_buf_))) {
        server_->send(500, "application/json", wifi_json_buf_);
      } else {
        server_->send(500, "text/plain", "response_too_large");
      }
      return;
    }
    if (result != BuildJsonResult::kOk) {
      server_->send(400, "text/plain", "Invalid data");
      return;
    }
    if (!storage_->WriteFileAtomic(kWifiConfigPath, wifi_json_buf_)) {
      server_->send(500, "text/plain", "Write failed");
      return;
    }
    char log_buf[160];
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[CFG] wifi.json saved ssid=%s",
                  ssid);
    Util::Logger::Info(log_buf);
    if (event_queue_) {
      Core::Event event{};
      event.type = Core::EventType::kWifiConfigUpdated;
      event_queue_->Push(event);
    }
    server_->send(200, "application/json", wifi_json_buf_);
  });

  server_->on("/api/networks", HTTP_DELETE, [this]() {
    if (!storage_) {
      server_->send(500, "text/plain", "Storage unavailable");
      return;
    }
    const String ssid = server_->arg("ssid");
    if (ssid.length() == 0) {
      server_->send(400, "text/plain", "SSID required");
      return;
    }
    wifi_list_.count = 0;
    LoadNetworksFromStorage(storage_, wifi_list_, wifi_json_buf_, sizeof(wifi_json_buf_));
    if (wifi_list_.count == 0) {
      server_->send(404, "text/plain", "Not found");
      return;
    }
    if (!RemoveNetwork(wifi_list_, ssid.c_str())) {
      server_->send(404, "text/plain", "Not found");
      return;
    }
    if (wifi_list_.count == 0) {
      server_->send(400, "text/plain", "No networks left");
      return;
    }
    const BuildJsonResult result = BuildNetworksJson(wifi_list_, wifi_json_buf_, sizeof(wifi_json_buf_));
    if (result == BuildJsonResult::kTooLarge) {
      if (WriteTooLargeError(wifi_json_buf_, sizeof(wifi_json_buf_))) {
        server_->send(500, "application/json", wifi_json_buf_);
      } else {
        server_->send(500, "text/plain", "response_too_large");
      }
      return;
    }
    if (result != BuildJsonResult::kOk) {
      server_->send(400, "text/plain", "Invalid data");
      return;
    }
    if (!storage_->WriteFileAtomic(kWifiConfigPath, wifi_json_buf_)) {
      server_->send(500, "text/plain", "Write failed");
      return;
    }
    if (event_queue_) {
      Core::Event event{};
      event.type = Core::EventType::kWifiConfigUpdated;
      event_queue_->Push(event);
    }
    server_->send(200, "application/json", wifi_json_buf_);
  });

  server_->on("/status", HTTP_GET, [this]() {
    char payload[256];
    const bool sta_connected = WiFi.status() == WL_CONNECTED;
    String sta_ip = WiFi.localIP().toString();
    String ap_ip = WiFi.softAPIP().toString();
    const char* device_id = device_id_ ? device_id_ : "device";
    char ap_ssid[64];
    std::snprintf(ap_ssid, sizeof(ap_ssid), "Grovika-%s", device_id);
    std::snprintf(payload,
                  sizeof(payload),
                  "{\"sta_connected\":%s,\"sta_ip\":\"%s\",\"ap_ssid\":\"%s\",\"ap_ip\":\"%s\"}",
                  sta_connected ? "true" : "false",
                  sta_ip.c_str(),
                  ap_ssid,
                  ap_ip.c_str());
    server_->send(200, "application/json", payload);
  });

  server_->begin();
  server_started_ = true;
#endif
}

void WebConfigService::Loop(Core::Context& ctx) {
  (void)ctx;
#if defined(ARDUINO)
  if (server_started_ && server_) {
    server_->handleClient();
  }
#endif
}

}
