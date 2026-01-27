/*
 * Chto v faile: realizaciya MQTT servisa i svyazi s event queue.
 * Rol v arhitekture: services.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe services.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "services/MqttService.h"

#include <cstdio>
#include <cstring>
#include <string>

#include "core/EventQueue.h"
#include "services/Topics.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#endif

namespace Services {

namespace {
struct MqttDefaults {
  const char* host;
  uint16_t port;
  const char* user;
  const char* pass;
};

static const MqttDefaults kMqttDefaults = {
    "growerhub.ru",
    1883,
    "mosquitto-admin",
    "qazwsxedc"};

static const uint32_t kReconnectIntervalMs = 5000;
#if defined(DEBUG_MQTT_DIAG)
// Interval publikacii diagnosticheskih soobshcheniy.
static const uint32_t kDiagIntervalMs = 10000;
#endif

static const char* MqttRcToString(int rc) {
  switch (rc) {
    case -4:
      return "connection_timeout";
    case -3:
      return "connection_lost";
    case -2:
      return "connect_failed";
    case -1:
      return "disconnected";
    case 0:
      return "connected";
    case 1:
      return "bad_protocol";
    case 2:
      return "bad_client_id";
    case 3:
      return "unavailable";
    case 4:
      return "bad_credentials";
    case 5:
      return "unauthorized";
    default:
      return "unknown";
  }
}
}

#if defined(ARDUINO)
MqttService* MqttService::active_instance_ = nullptr;
#endif

MqttService::MqttService()
#if defined(ARDUINO)
    : mqtt_client_(wifi_client_)
#endif
{}

void MqttService::Init(Core::Context& ctx) {
  event_queue_ = ctx.event_queue;
  device_id_ = ctx.device_id;
  last_attempt_ms_ = 0;
  last_connected_ = false;
  wifi_ready_ = false;
  last_skip_log_ms_ = 0;
  Util::Logger::Info("[MQTT] init");
#if defined(ARDUINO)
  active_instance_ = this;
  mqtt_client_.setCallback(MqttService::OnMessageThunk);
  mqtt_host_ = kMqttDefaults.host;
  mqtt_port_ = kMqttDefaults.port;
  mqtt_user_ = kMqttDefaults.user;
  mqtt_pass_ = kMqttDefaults.pass;

  mqtt_client_.setServer(mqtt_host_, mqtt_port_);
  char log_buf[192];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[MQTT] set_server host=%s port=%u",
                mqtt_host_,
                static_cast<unsigned int>(mqtt_port_));
  Util::Logger::Info(log_buf);
#endif
}

bool MqttService::Publish(const char* topic, const char* payload, bool retain, int qos) {
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
  const size_t payload_len = std::strlen(payload);
#if defined(MQTT_MAX_PACKET_SIZE)
  const size_t max_packet_size = MQTT_MAX_PACKET_SIZE;
#else
  const size_t max_packet_size = 0;
#endif
  const char* client_id = device_id_ ? device_id_ : "device";
  const char* user = mqtt_user_ ? mqtt_user_ : "";
  char log_buf[192];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[MQTT] publish host=%s port=%u client_id=%s user=%s",
                mqtt_host_ ? mqtt_host_ : "",
                static_cast<unsigned int>(mqtt_port_),
                client_id,
                user);
  Util::Logger::Info(log_buf);
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[MQTT] publish topic=%s qos=%d retained=%s",
                topic,
                qos,
                retain ? "true" : "false");
  Util::Logger::Info(log_buf);
  if (!mqtt_client_.connected()) {
    const int rc = mqtt_client_.state();
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[MQTT] publish result ok=false rc=%d reason=%s ack=na",
                  rc,
                  MqttRcToString(rc));
    Util::Logger::Info(log_buf);
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[MQTT] publish diag payload_len=%u max_packet=%u connected=false state=%d",
                  static_cast<unsigned int>(payload_len),
                  static_cast<unsigned int>(max_packet_size),
                  rc);
    Util::Logger::Info(log_buf);
    return false;
  }
  bool ok = false;
  if (qos <= 0) {
    ok = mqtt_client_.publish(topic, payload, retain);
  } else if (qos == 1) {
    ok = mqtt_client_.publish(topic, payload, retain);
  } else {
    ok = false;
  }
  const int rc = mqtt_client_.state();
  const char* ack_note = qos > 0 ? "na_pubsubclient" : "na_qos0";
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[MQTT] publish result ok=%s rc=%d reason=%s ack=%s",
                ok ? "true" : "false",
                rc,
                MqttRcToString(rc),
                ack_note);
  Util::Logger::Info(log_buf);
  if (!ok) {
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[MQTT] publish diag payload_len=%u max_packet=%u connected=%s state=%d",
                  static_cast<unsigned int>(payload_len),
                  static_cast<unsigned int>(max_packet_size),
                  mqtt_client_.connected() ? "true" : "false",
                  rc);
    Util::Logger::Info(log_buf);
  }
  return ok;
#else
  return connected_;
#endif
}

bool MqttService::Subscribe(const char* topic, int qos) {
  if (!topic) {
    return false;
  }
#if defined(ARDUINO)
  StorePendingSub(topic, qos);
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
  const uint32_t now_ms = millis();
  if (!wifi_ready_) {
    if (last_skip_log_ms_ == 0 ||
        static_cast<int32_t>(now_ms - last_skip_log_ms_) >= static_cast<int32_t>(kReconnectIntervalMs)) {
      Util::Logger::Info("[NET] wifi not ready -> skip mqtt");
      last_skip_log_ms_ = now_ms;
    }
    return;
  }
  last_skip_log_ms_ = 0;
  const bool connected = mqtt_client_.connected();
  if (connected) {
    mqtt_client_.loop();
  }
  if (!connected && last_connected_) {
    const int rc = mqtt_client_.state();
    char log_buf[192];
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[MQTT] disconnected rc=%d reason=%s",
                  rc,
                  MqttRcToString(rc));
    Util::Logger::Info(log_buf);
  }
  if (!connected) {
    if (now_ms - last_attempt_ms_ >= kReconnectIntervalMs) {
      TryConnect(now_ms);
    }
  }
  last_connected_ = mqtt_client_.connected();
#if defined(DEBUG_MQTT_DIAG)
  if (connected) {
    const uint32_t now_ms = millis();
    if (diag_next_ms_ == 0 || static_cast<int32_t>(now_ms - diag_next_ms_) >= 0) {
      diag_next_ms_ = now_ms + kDiagIntervalMs;
      ++diag_seq_;
      char topic[128];
      if (device_id_ &&
          Services::Topics::BuildDeviceTopic(topic, sizeof(topic), device_id_, "diag")) {
        char payload[96];
        std::snprintf(payload,
                      sizeof(payload),
                      "{\"seq\":%lu}",
                      static_cast<unsigned long>(diag_seq_));
        Publish(topic, payload, false, 0);
      }
    }
  }
#endif
#endif
}

void MqttService::SetWifiReady(bool ready) {
  if (ready == wifi_ready_) {
    return;
  }
  wifi_ready_ = ready;
#if defined(ARDUINO)
  if (wifi_ready_) {
    Util::Logger::Info("[NET] wifi ready -> start mqtt");
    const uint32_t now_ms = millis();
    last_attempt_ms_ = 0;
    TryConnect(now_ms);
  } else {
    Util::Logger::Info("[NET] wifi lost -> stop mqtt");
    if (mqtt_client_.connected()) {
      mqtt_client_.disconnect();
    }
    last_connected_ = false;
    last_attempt_ms_ = 0;
  }
#else
  Util::Logger::Info(ready ? "[NET] wifi ready -> start mqtt" : "[NET] wifi lost -> stop mqtt");
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
bool MqttService::TryConnect(uint32_t now_ms) {
  if (!mqtt_host_ || mqtt_port_ == 0) {
    Util::Logger::Info("[MQTT] connect skip: no host");
    last_attempt_ms_ = now_ms;
    return false;
  }

  const char* client_id = device_id_ ? device_id_ : "device";
  const bool has_user = mqtt_user_ && mqtt_user_[0] != '\0';
  const bool has_pass = mqtt_pass_ && mqtt_pass_[0] != '\0';

  char log_buf[192];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[MQTT] connect attempt client_id=%s user=%s",
                client_id,
                has_user ? "present" : "absent");
  Util::Logger::Info(log_buf);

  bool connected = false;
  if (has_user || has_pass) {
    const char* user = has_user ? mqtt_user_ : "";
    const char* pass = has_pass ? mqtt_pass_ : "";
    connected = mqtt_client_.connect(client_id, user, pass);
  } else {
    connected = mqtt_client_.connect(client_id);
  }

  if (connected) {
    Util::Logger::Info("[MQTT] connected");
    SubscribePending();
  } else {
    const int rc = mqtt_client_.state();
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[MQTT] connect failed rc=%d reason=%s",
                  rc,
                  MqttRcToString(rc));
    Util::Logger::Info(log_buf);
  }

  last_attempt_ms_ = now_ms;
  last_connected_ = connected;
  return connected;
}

bool MqttService::StorePendingSub(const char* topic, int qos) {
  if (!topic) {
    return false;
  }
  for (size_t i = 0; i < kMaxPendingSubs; ++i) {
    if (pending_subs_[i].used && std::strcmp(pending_subs_[i].topic, topic) == 0) {
      pending_subs_[i].qos = qos;
      return true;
    }
  }
  for (size_t i = 0; i < kMaxPendingSubs; ++i) {
    if (!pending_subs_[i].used) {
      std::strncpy(pending_subs_[i].topic, topic, sizeof(pending_subs_[i].topic) - 1);
      pending_subs_[i].topic[sizeof(pending_subs_[i].topic) - 1] = '\0';
      pending_subs_[i].qos = qos;
      pending_subs_[i].used = true;
      return true;
    }
  }
  Util::Logger::Info("[MQTT] pending_subs full");
  return false;
}

void MqttService::SubscribePending() {
  for (size_t i = 0; i < kMaxPendingSubs; ++i) {
    if (!pending_subs_[i].used) {
      continue;
    }
    mqtt_client_.subscribe(pending_subs_[i].topic, pending_subs_[i].qos);
  }
}

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

  const bool truncated = length >= Core::kMqttPayloadMax;
  const char* topic_str = topic ? topic : "";
  std::string log_line = "[MQTT] rx topic=";
  log_line += topic_str;
  log_line += " payload=";
  log_line += payload_buf;
  if (truncated) {
    log_line += " truncated=true";
  }
  Util::Logger::Info(log_line.c_str());

  PushEvent(topic, payload_buf);
}
#endif

}
