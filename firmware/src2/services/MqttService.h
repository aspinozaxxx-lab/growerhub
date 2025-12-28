#pragma once

#include <cstdint>
#include "core/Context.h"

#if defined(ARDUINO)
#include <PubSubClient.h>
#endif

namespace Services {

class MqttService {
 public:
  using PublishHook = void (*)(const char* topic, const char* payload, bool retain, int qos);

  MqttService();

  void Init(Core::Context& ctx);
  bool Publish(const char* topic, const char* payload, bool retain, int qos);
  bool Subscribe(const char* topic, int qos);
  bool IsConnected();
  void Loop();

#if defined(UNIT_TEST)
  void SetConnectedForTests(bool connected);
  void SetPublishHook(PublishHook hook);
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
