#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace Core {

enum class EventType : uint8_t {
  kNone = 0,
  kHeartbeat = 1,
  kCustom = 2,
  kMqttMessage = 3,
  kConfigUpdated = 4
};

static const size_t kMqttTopicMax = 64;
static const size_t kMqttPayloadMax = 256;

struct MqttMessage {
  char topic[kMqttTopicMax];
  char payload[kMqttPayloadMax];
};

struct Event {
  EventType type;
  uint32_t value;
  MqttMessage mqtt;
};

class EventQueue {
 public:
  static const size_t kCapacity = 16;

  bool Push(const Event& event);
  bool Pop(Event& event);
  size_t Size() const;

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
