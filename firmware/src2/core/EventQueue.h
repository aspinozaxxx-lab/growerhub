/*
 * Chto v faile: obyavleniya tipov sobytiy i ocheredi sobytiy.
 * Rol v arhitekture: core.
 * Naznachenie: publichnyi API i tipy dlya sloya core.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace Core {

enum class EventType : uint8_t {
  kNone = 0, // Pustoe sobytie.
  kHeartbeat = 1, // Periodicheskiy heartbeat.
  kCustom = 2, // Polzovatelskoe sobytie.
  kMqttMessage = 3, // MQTT soobshchenie v ocheredi.
  kConfigUpdated = 4, // Obnovlenie konfiguracii scenariev.
  kWifiStaUp = 5, // STA podklyuchilas k Wi-Fi.
  kWifiStaDown = 6, // STA otklyuchilas ot Wi-Fi.
  kWifiApUp = 7, // AP zapushchen.
  kWifiConfigUpdated = 8, // Obnovlena konfiguraciya Wi-Fi.
  kPumpStarted = 9, // Nasos zapushchen.
  kPumpStopped = 10, // Nasos ostanovlen.
  kRebootRequest = 11 // Zapros na reboot.
};

// Maksimalnaya dlina MQTT topika.
static const size_t kMqttTopicMax = 64;
// Maksimalnaya dlina MQTT payload.
static const size_t kMqttPayloadMax = 512;

struct MqttMessage {
  // Topik soobshcheniya.
  char topic[kMqttTopicMax];
  // Payload soobshcheniya.
  char payload[kMqttPayloadMax];
};

struct Event {
  // Tip sobytiya.
  EventType type;
  // Dop. chislennoe znachenie sobytiya.
  uint64_t value;
  // MQTT dannye dlya sobytiy tipa kMqttMessage.
  MqttMessage mqtt;
};

class EventQueue {
 public:
  // Emkost ocheredi sobytiy.
  static const size_t kCapacity = 16;

  /**
   * Pomeshchaet sobytie v ochered.
   * @param event Sobytie dlya dobavleniya.
   */
  bool Push(const Event& event);
  /**
   * Izvlekaet sobytie iz ocheredi.
   * @param event Vyhodnoy obiekt dlya polucheniya sobytiya.
   */
  bool Pop(Event& event);
  /**
   * Tekushchee kolichestvo sobytiy v ocheredi.
   */
  size_t Size() const;

  /**
   * Prohodit po ocheredi i vyzyvaet handler dlya kazhdogo sobytiya.
   * @param handler Fanktor obrabotki sobytiy.
   */
  template <typename Handler>
  void Drain(Handler handler) {
    Event event;
    while (Pop(event)) {
      handler(event);
    }
  }

 private:
  std::array<Event, kCapacity> buffer_;
  size_t head_ = 0;
  size_t tail_ = 0;
  size_t count_ = 0;
};

}
