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
#include "services/StorageService.h"
#include "services/WiFiService.h"
#include "services/website/WebsiteContent.h"
#include "util/JsonUtil.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <WebServer.h>
#include <WiFi.h>
#endif

namespace Services {

static const char* kWifiConfigPath = "/cfg/wifi.json";
static const size_t kWifiJsonBufferSize = 2048;

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

static bool LoadNetworksFromStorage(StorageService* storage, WiFiNetworkList& out) {
  out.count = 0;
  if (!storage || !storage->Exists(kWifiConfigPath)) {
    return false;
  }
  char json[kWifiJsonBufferSize];
  if (!storage->ReadFile(kWifiConfigPath, json, sizeof(json))) {
    return false;
  }
  return WiFiService::ParseWifiConfig(json, out);
}

static bool BuildNetworksJson(const WiFiNetworkList& list, char* out, size_t out_size) {
  const char* ssids[kWifiMaxNetworks];
  const char* passwords[kWifiMaxNetworks];
  for (size_t i = 0; i < list.count; ++i) {
    ssids[i] = list.entries[i].ssid;
    passwords[i] = list.entries[i].password;
  }
  return Util::EncodeWifiConfig(ssids, passwords, list.count, out, out_size);
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
  Util::Logger::Info("[CFG] init WebConfigService");

#if defined(ARDUINO)
  if (!server_) {
    server_ = new WebServer(80);
  }

  server_->on("/", HTTP_GET, [this]() {
    server_->send(200, "text/html", Website::Html());
  });

  server_->on("/style.css", HTTP_GET, [this]() {
    server_->send(200, "text/css", Website::Css());
  });

  server_->on("/app.js", HTTP_GET, [this]() {
    server_->send(200, "application/javascript", Website::Js());
  });

  server_->on("/api/networks", HTTP_GET, [this]() {
    WiFiNetworkList list{};
    if (LoadNetworksFromStorage(storage_, list) && list.count > 0) {
      char json[kWifiJsonBufferSize];
      if (!BuildNetworksJson(list, json, sizeof(json))) {
        server_->send(500, "text/plain", "Encode failed");
        return;
      }
      server_->send(200, "application/json", json);
      return;
    }
    char empty_json[64];
    std::snprintf(empty_json,
                  sizeof(empty_json),
                  "{\"schema_version\":%u,\"networks\":[]}",
                  static_cast<unsigned int>(Util::kWifiSchemaVersion));
    server_->send(200, "application/json", empty_json);
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

    WiFiNetworkList list{};
    LoadNetworksFromStorage(storage_, list);
    if (!UpsertNetwork(list, ssid, password)) {
      server_->send(400, "text/plain", "Too many networks");
      return;
    }

    char json[kWifiJsonBufferSize];
    if (!BuildNetworksJson(list, json, sizeof(json))) {
      server_->send(400, "text/plain", "Invalid data");
      return;
    }
    if (!storage_->WriteFileAtomic(kWifiConfigPath, json)) {
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
    server_->send(200, "application/json", json);
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
    WiFiNetworkList list{};
    LoadNetworksFromStorage(storage_, list);
    if (list.count == 0) {
      server_->send(404, "text/plain", "Not found");
      return;
    }
    if (!RemoveNetwork(list, ssid.c_str())) {
      server_->send(404, "text/plain", "Not found");
      return;
    }
    if (list.count == 0) {
      server_->send(400, "text/plain", "No networks left");
      return;
    }
    char json[kWifiJsonBufferSize];
    if (!BuildNetworksJson(list, json, sizeof(json))) {
      server_->send(400, "text/plain", "Invalid data");
      return;
    }
    if (!storage_->WriteFileAtomic(kWifiConfigPath, json)) {
      server_->send(500, "text/plain", "Write failed");
      return;
    }
    if (event_queue_) {
      Core::Event event{};
      event.type = Core::EventType::kWifiConfigUpdated;
      event_queue_->Push(event);
    }
    server_->send(200, "application/json", json);
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
