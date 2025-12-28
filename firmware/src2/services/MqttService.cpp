#include "services/MqttService.h"

#include <cstring>

#include "core/EventQueue.h"
#include "util/Logger.h"

namespace Services {

#if defined(ARDUINO)
MqttService* MqttService::active_instance_ = nullptr;
#endif

MqttService::MqttService() {}

void MqttService::Init(Core::Context& ctx) {
  event_queue_ = ctx.event_queue;
  Util::Logger::Info("init MqttService");
#if defined(ARDUINO)
  active_instance_ = this;
  mqtt_client_.setCallback(MqttService::OnMessageThunk);
#endif
}

bool MqttService::Publish(const char* topic, const char* payload, bool retain, int qos) {
  (void)qos;
  if (!topic || !payload) {
    return false;
  }
#if defined(UNIT_TEST)
  if (publish_hook_) {
    publish_hook_(topic, payload, retain, qos);
    return true;
  }
#endif
#if defined(ARDUINO)
  if (!mqtt_client_.connected()) {
    return false;
  }
  return mqtt_client_.publish(topic, payload, retain);
#else
  return connected_;
#endif
}

bool MqttService::Subscribe(const char* topic, int qos) {
  if (!topic) {
    return false;
  }
#if defined(ARDUINO)
  if (!mqtt_client_.connected()) {
    return false;
  }
  return mqtt_client_.subscribe(topic, qos);
#else
  (void)qos;
  return connected_;
#endif
}

bool MqttService::IsConnected() {
#if defined(ARDUINO)
  return mqtt_client_.connected();
#else
  return connected_;
#endif
}

void MqttService::Loop() {
#if defined(ARDUINO)
  if (mqtt_client_.connected()) {
    mqtt_client_.loop();
  }
#endif
}

#if defined(UNIT_TEST)
void MqttService::SetConnectedForTests(bool connected) {
  connected_ = connected;
}

void MqttService::SetPublishHook(PublishHook hook) {
  publish_hook_ = hook;
}

void MqttService::InjectMessage(const char* topic, const char* payload) {
  PushEvent(topic, payload);
}
#endif

void MqttService::PushEvent(const char* topic, const char* payload) {
  if (!event_queue_ || !topic || !payload) {
    return;
  }

  Core::Event event{};
  event.type = Core::EventType::kMqttMessage;
  event.value = 0;

  std::strncpy(event.mqtt.topic, topic, Core::kMqttTopicMax - 1);
  event.mqtt.topic[Core::kMqttTopicMax - 1] = '\0';
  std::strncpy(event.mqtt.payload, payload, Core::kMqttPayloadMax - 1);
  event.mqtt.payload[Core::kMqttPayloadMax - 1] = '\0';

  event_queue_->Push(event);
}

#if defined(ARDUINO)
void MqttService::OnMessageThunk(char* topic, uint8_t* payload, unsigned int length) {
  if (active_instance_) {
    active_instance_->HandleMessage(topic, payload, length);
  }
}

void MqttService::HandleMessage(char* topic, uint8_t* payload, unsigned int length) {
  if (!payload) {
    return;
  }

  char payload_buf[Core::kMqttPayloadMax];
  unsigned int copy_len = length;
  if (copy_len >= Core::kMqttPayloadMax) {
    copy_len = Core::kMqttPayloadMax - 1;
  }
  std::memcpy(payload_buf, payload, copy_len);
  payload_buf[copy_len] = '\0';

  PushEvent(topic, payload_buf);
}
#endif

}
