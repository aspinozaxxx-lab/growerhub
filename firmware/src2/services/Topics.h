/*
 * Chto v faile: obyavleniya postroeniya i proverki MQTT-topikov.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdio>
#include <cstring>

namespace Services {
namespace Topics {

// Suffiks topika komand.
static constexpr const char* kCmdSuffix = "cmd";
// Suffiks topika konfiguracii.
static constexpr const char* kCfgSuffix = "cfg";
// Suffiks topika sostoyaniya.
static constexpr const char* kStateSuffix = "state";
// Suffiks topika ack.
static constexpr const char* kAckSuffix = "state/ack";
// Suffiks topika sobytiy.
static constexpr const char* kEventsSuffix = "events";

/**
 * Stroit topik dlya ustroistva s zadannym suffiksom.
 * @param out Vyhodnoy bufer dlya topika.
 * @param out_size Razmer bufera v baytah.
 * @param device_id Identifikator ustroistva.
 * @param suffix Suffiks topika.
 */
inline bool BuildDeviceTopic(char* out, size_t out_size, const char* device_id, const char* suffix) {
  if (!out || out_size == 0 || !device_id || !suffix) {
    return false;
  }
  const int written = std::snprintf(out, out_size, "gh/dev/%s/%s", device_id, suffix);
  return written > 0 && static_cast<size_t>(written) < out_size;
}

/**
 * Stroit topik komand.
 * @param out Vyhodnoy bufer dlya topika.
 * @param out_size Razmer bufera v baytah.
 * @param device_id Identifikator ustroistva.
 */
inline bool BuildCmdTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kCmdSuffix);
}

/**
 * Stroit topik konfiguracii.
 * @param out Vyhodnoy bufer dlya topika.
 * @param out_size Razmer bufera v baytah.
 * @param device_id Identifikator ustroistva.
 */
inline bool BuildCfgTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kCfgSuffix);
}

/**
 * Stroit topik sostoyaniya.
 * @param out Vyhodnoy bufer dlya topika.
 * @param out_size Razmer bufera v baytah.
 * @param device_id Identifikator ustroistva.
 */
inline bool BuildStateTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kStateSuffix);
}

/**
 * Stroit topik ack.
 * @param out Vyhodnoy bufer dlya topika.
 * @param out_size Razmer bufera v baytah.
 * @param device_id Identifikator ustroistva.
 */
inline bool BuildAckTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kAckSuffix);
}

/**
 * Stroit topik sobytiy.
 * @param out Vyhodnoy bufer dlya topika.
 * @param out_size Razmer bufera v baytah.
 * @param device_id Identifikator ustroistva.
 */
inline bool BuildEventsTopic(char* out, size_t out_size, const char* device_id) {
  return BuildDeviceTopic(out, out_size, device_id, kEventsSuffix);
}

/**
 * Proveryaet, yavlyaetsya li topik komandnym.
 * @param topic Vhodnoy topik dlya proverki.
 * @param device_id Identifikator ustroistva.
 */
inline bool IsCmdTopic(const char* topic, const char* device_id) {
  char expected[128];
  if (!BuildCmdTopic(expected, sizeof(expected), device_id)) {
    return false;
  }
  return std::strcmp(topic, expected) == 0;
}

/**
 * Proveryaet, yavlyaetsya li topik konfiguracionnym.
 * @param topic Vhodnoy topik dlya proverki.
 * @param device_id Identifikator ustroistva.
 */
inline bool IsCfgTopic(const char* topic, const char* device_id) {
  char expected[128];
  if (!BuildCfgTopic(expected, sizeof(expected), device_id)) {
    return false;
  }
  return std::strcmp(topic, expected) == 0;
}

}
}
