/*
 * Chto v faile: obyavleniya MQTT servisa i svyazi s event queue.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include "core/Context.h"

#if defined(ARDUINO)
#include <PubSubClient.h>
#include <WiFiClient.h>
#endif

namespace Services {

class MqttService {
 public:
  // Tip callback dlya perehvata publikacii v testah.
  using PublishHook = void (*)(const char* topic, const char* payload, bool retain, int qos);

  /**
   * Sozdaet MQTT servis.
   */
  MqttService();

  /**
   * Init servisa MQTT.
   * @param ctx Kontekst s ocheredyu sobytiy.
   */
  void Init(Core::Context& ctx);
  /**
   * Publikuet soobshchenie v MQTT.
   * @param topic MQTT topik.
   * @param payload MQTT payload.
   * @param retain Flag retained.
   * @param qos Uroven QoS.
   */
  bool Publish(const char* topic, const char* payload, bool retain, int qos);
  /**
   * Podpisyvaetsya na topik.
   * @param topic MQTT topik.
   * @param qos Uroven QoS.
   */
  bool Subscribe(const char* topic, int qos);
  /**
   * Proveryaet sostoyanie podklyucheniya.
   */
  bool IsConnected();
  /**
   * Obrabatyvaet MQTT loop (Arduino).
   */
  void Loop();

#if defined(UNIT_TEST)
  /**
   * Ustanavlivaet sostoyanie podklyucheniya dlya testov.
   * @param connected Flag podklyucheniya.
   */
  void SetConnectedForTests(bool connected);
  /**
   * Ustanavlivaet hook dlya publikacii v testah.
   * @param hook Ukazatel na callback publikacii.
   */
  void SetPublishHook(PublishHook hook);
  /**
   * Vstavlyaet soobshchenie v ochered sobytiy.
   * @param topic MQTT topik.
   * @param payload MQTT payload.
   */
  void InjectMessage(const char* topic, const char* payload);
#endif

 private:
#if defined(ARDUINO)
  bool TryConnect(uint32_t now_ms);
  bool StorePendingSub(const char* topic, int qos);
  void SubscribePending();
  void HandleMessage(char* topic, uint8_t* payload, unsigned int length);
  static void OnMessageThunk(char* topic, uint8_t* payload, unsigned int length);
#endif
  void PushEvent(const char* topic, const char* payload);

  Core::EventQueue* event_queue_ = nullptr;
  const char* device_id_ = nullptr;
  uint32_t last_attempt_ms_ = 0;
  bool last_connected_ = false;
  bool connected_ = false;

#if defined(ARDUINO)
  static constexpr size_t kPendingTopicMax = 128;
  static constexpr size_t kMaxPendingSubs = 4;
  struct PendingSub {
    char topic[kPendingTopicMax];
    int qos;
    bool used;
  };

  static MqttService* active_instance_;
  WiFiClient wifi_client_;
  PubSubClient mqtt_client_;
  const char* mqtt_host_ = nullptr;
  uint16_t mqtt_port_ = 0;
  const char* mqtt_user_ = nullptr;
  const char* mqtt_pass_ = nullptr;
  PendingSub pending_subs_[kMaxPendingSubs]{};
#if defined(DEBUG_MQTT_DIAG)
  // Schetchik diagnosticheskih publikacij.
  uint32_t diag_seq_ = 0;
  // Sleduyuschee vremya diagnosticheskoy publikacii.
  uint32_t diag_next_ms_ = 0;
#endif
#endif

#if defined(UNIT_TEST)
  PublishHook publish_hook_ = nullptr;
#endif
};

}
