#pragma once

#include <cstddef>
#include <cstdio>
#include <cstring>

namespace Services {
namespace Topics {

static constexpr const char* kCmdSuffix = "cmd";
static constexpr const char* kCfgSuffix = "cfg";
static constexpr const char* kStateSuffix = "state";
static constexpr const char* kAckSuffix = "state/ack";

inline bool BuildDeviceTopic(char* out, size_t out_size, const char* device_id, const char* suffix) {
  if (!out || out_size == 0 || !device_id || !suffix) {
    return false;
  }
  const int written = std::snprintf(out, out_size, "gh/dev/%s/%s", device_id, suffix);
  return written > 0 && static_cast<size_t>(written) < out_size;
}

inline bool BuildCmdTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kCmdSuffix);
}

inline bool BuildCfgTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kCfgSuffix);
}

inline bool BuildStateTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kStateSuffix);
}

inline bool BuildAckTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kAckSuffix);
}

inline bool IsCmdTopic(const char* topic, const char* device_id) {
  char expected[128];
  if (!BuildCmdTopic(expected, sizeof(expected), device_id)) {
    return false;
  }
  return std::strcmp(topic, expected) == 0;
}

inline bool IsCfgTopic(const char* topic, const char* device_id) {
  char expected[128];
  if (!BuildCfgTopic(expected, sizeof(expected), device_id)) {
    return false;
  }
  return std::strcmp(topic, expected) == 0;
}

}
}
