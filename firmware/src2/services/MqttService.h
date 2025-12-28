/*
 * Chto v faile: obyavleniya MQTT servisa i svyazi s event queue.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>
#include "core/Context.h"

#if defined(ARDUINO)
#include <PubSubClient.h>
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
  void HandleMessage(char* topic, uint8_t* payload, unsigned int length);
  static void OnMessageThunk(char* topic, uint8_t* payload, unsigned int length);
#endif
  void PushEvent(const char* topic, const char* payload);

  Core::EventQueue* event_queue_ = nullptr;
  bool connected_ = false;

#if defined(ARDUINO)
  static MqttService* active_instance_;
  PubSubClient mqtt_client_;
#endif

#if defined(UNIT_TEST)
  PublishHook publish_hook_ = nullptr;
#endif
};

}
