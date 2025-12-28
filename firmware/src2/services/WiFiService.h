/*
 * Chto v faile: obyavleniya servisa upravleniya Wi-Fi i AP.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>

#include "core/Context.h"
#include "core/EventQueue.h"

namespace Services {

// Maksimalnoe kolichestvo setei v spiske.
static const size_t kWifiMaxNetworks = 10;
// Maksimalnaya dlina SSID.
static const size_t kWifiSsidMaxLen = 32;
// Maksimalnaya dlina parolya.
static const size_t kWifiPasswordMaxLen = 64;

struct WiFiNetwork {
  // SSID seti.
  char ssid[kWifiSsidMaxLen + 1];
  // Parol seti.
  char password[kWifiPasswordMaxLen + 1];
};

struct WiFiNetworkList {
  // Kolichestvo zapisei v spiske.
  size_t count;
  // Spisok setei.
  WiFiNetwork entries[kWifiMaxNetworks];
};

class WiFiService {
 public:
  /**
   * Init servisa Wi-Fi.
   * @param ctx Kontekst s hranilishchem i ocheredyu sobytiy.
   */
  void Init(Core::Context& ctx);
  /**
   * Periodicheskiy loop Wi-Fi logiki.
   * @param ctx Kontekst s zavisimostyami servisa.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void Loop(Core::Context& ctx, uint32_t now_ms);
  /**
   * Obrabotka sobytiy iz ocheredi.
   * @param ctx Kontekst s zavisimostyami servisa.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event);
  /**
   * Vozvrashaet predpochtennyi spisok setei.
   */
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
  const char* device_id_ = nullptr;
  WiFiNetworkList preferred_{};
  size_t sta_index_ = 0;
  uint32_t last_attempt_ms_ = 0;
  bool ap_started_ = false;
  int last_status_ = -1;
};

}
