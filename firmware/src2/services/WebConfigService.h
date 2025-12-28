/*
 * Chto v faile: obyavleniya web-nastroiki Wi-Fi i zapisi konfiguracii.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>

#include "core/Context.h"

#if defined(ARDUINO)
class WebServer;
#endif

namespace Services {

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
 StorageService* storage_ = nullptr;
 Core::EventQueue* event_queue_ = nullptr;
 const char* device_id_ = nullptr;
#if defined(ARDUINO)
  WebServer* server_ = nullptr;
  bool server_started_ = false;
#endif
};

}
