/*
 * Chto v faile: obyavleniya web-nastroiki Wi-Fi i zapisi konfiguracii.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>

#include "core/Context.h"
#include "services/WiFiService.h"

#if defined(ARDUINO)
class WebServer;
#endif

namespace Services {

class MqttService;

class WebConfigService {
 public:
  /**
   * Init web-servisa konfiguracii.
   * @param ctx Kontekst s hranilishchem i ocheredyu sobytiy.
   */
  void Init(Core::Context& ctx);
  /**
   * Obrabotka zaprosov web-servera.
   * @param ctx Kontekst s zavisimostyami servisa.
   */
  void Loop(Core::Context& ctx);
  /**
   * Stroit JSON dlya Wi-Fi konfiguracii.
   * @param ssid SSID seti.
   * @param password Parol seti.
   * @param out Bufer dlya JSON stroki.
   * @param out_size Razmer bufera v baytah.
   */
 static bool BuildWifiConfigJson(const char* ssid, const char* password, char* out, size_t out_size);

 private:
 static const size_t kWifiJsonBufferSize = 2048;
 StorageService* storage_ = nullptr;
 Core::EventQueue* event_queue_ = nullptr;
 const char* device_id_ = nullptr;
 // Ukazatel na MQTT servis.
 MqttService* mqtt_ = nullptr;
 WiFiNetworkList wifi_list_{};
 char wifi_json_buf_[kWifiJsonBufferSize] = {};
#if defined(ARDUINO)
  WebServer* server_ = nullptr;
  bool server_started_ = false;
#endif
};

}
