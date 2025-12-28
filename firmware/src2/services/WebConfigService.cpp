#include "services/WebConfigService.h"

#include <cstdio>

#include "core/EventQueue.h"
#include "services/StorageService.h"
#include "util/JsonUtil.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <WebServer.h>
#include <WiFi.h>
#endif

namespace Services {

static const char kWebHtml[] =
    "<!doctype html><html><head><meta charset=\"utf-8\">"
    "<title>Wi-Fi setup</title></head><body>"
    "<h1>Wi-Fi setup</h1>"
    "<form method=\"post\" action=\"/save\">"
    "<label>SSID</label><br/>"
    "<input name=\"ssid\" maxlength=\"32\"/>"
    "<br/>"
    "<label>Password</label><br/>"
    "<input name=\"password\" type=\"password\" maxlength=\"64\"/>"
    "<br/>"
    "<button type=\"submit\">Save</button>"
    "</form></body></html>";

bool WebConfigService::BuildWifiConfigJson(const char* ssid,
                                           const char* password,
                                           char* out,
                                           size_t out_size) {
  return Util::EncodeWifiConfig(ssid, password, out, out_size);
}

void WebConfigService::Init(Core::Context& ctx) {
  storage_ = ctx.storage;
  event_queue_ = ctx.event_queue;
  device_id_ = ctx.device_id;
  Util::Logger::Info("init WebConfigService");

#if defined(ARDUINO)
  if (!server_) {
    server_ = new WebServer(80);
  }

  server_->on("/", HTTP_GET, [this]() {
    server_->send(200, "text/html", kWebHtml);
  });

  server_->on("/save", HTTP_POST, [this]() {
    if (!storage_) {
      server_->send(500, "text/plain", "Storage unavailable");
      return;
    }
    const String ssid = server_->arg("ssid");
    const String password = server_->arg("password");
    if (ssid.length() == 0) {
      server_->send(400, "text/plain", "SSID required");
      return;
    }
    char json[256];
    if (!BuildWifiConfigJson(ssid.c_str(), password.c_str(), json, sizeof(json))) {
      server_->send(400, "text/plain", "Invalid data");
      return;
    }
    if (!storage_->WriteFileAtomic("/cfg/wifi.json", json)) {
      server_->send(500, "text/plain", "Write failed");
      return;
    }
    if (event_queue_) {
      Core::Event event{};
      event.type = Core::EventType::kWifiConfigUpdated;
      event_queue_->Push(event);
    }
    server_->send(200, "text/plain", "Saved, reconnecting...");
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
