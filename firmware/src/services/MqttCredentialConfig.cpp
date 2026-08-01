/*
 * Chto v faile: realizaciya zagruzki seriynoy MQTT konfiguracii.
 * Rol v arhitekture: services.
 * Naznachenie: chtenie i strogaya validaciya /cfg/mqtt.json.
 */

#include "services/MqttCredentialConfig.h"

#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "services/StorageService.h"

namespace Services {

namespace {
const char* kMqttConfigPath = "/cfg/mqtt.json";

const char* SkipWs(const char* value) {
  while (value && *value && std::isspace(static_cast<unsigned char>(*value))) {
    ++value;
  }
  return value;
}

bool ExtractUint(const char* json, const char* key, unsigned long* out) {
  if (!json || !key || !out) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  const char* colon = key_pos ? std::strchr(key_pos + std::strlen(pattern), ':') : nullptr;
  const char* value = colon ? SkipWs(colon + 1) : nullptr;
  if (!value || !std::isdigit(static_cast<unsigned char>(*value))) {
    return false;
  }
  char* end = nullptr;
  const unsigned long parsed = std::strtoul(value, &end, 10);
  const char* after_value = SkipWs(end);
  if (end == value || (*after_value != ',' && *after_value != '}')) {
    return false;
  }
  *out = parsed;
  return true;
}

bool ExtractString(const char* json, const char* key, char* out, size_t out_size) {
  if (!json || !key || !out || out_size == 0) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  const char* colon = key_pos ? std::strchr(key_pos + std::strlen(pattern), ':') : nullptr;
  const char* value = colon ? SkipWs(colon + 1) : nullptr;
  if (!value || *value != '"') {
    return false;
  }
  ++value;
  size_t length = 0;
  while (value[length] && value[length] != '"') {
    if (value[length] == '\\' || length + 1 >= out_size) {
      return false;
    }
    out[length] = value[length];
    ++length;
  }
  if (value[length] != '"' || length == 0) {
    return false;
  }
  const char* after_value = SkipWs(value + length + 1);
  if (*after_value != ',' && *after_value != '}') {
    return false;
  }
  out[length] = '\0';
  return true;
}

bool HasSingleKey(const char* json, const char* key) {
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* first = std::strstr(json, pattern);
  return first && !std::strstr(first + std::strlen(pattern), pattern);
}

bool HasObjectEnvelope(const char* json) {
  const char* begin = SkipWs(json);
  if (!begin || *begin != '{') {
    return false;
  }
  const char* end = begin + std::strlen(begin);
  while (end > begin && std::isspace(static_cast<unsigned char>(end[-1]))) {
    --end;
  }
  return end > begin && end[-1] == '}';
}

bool IsDeviceIdValid(const char* device_id) {
  static const char* kPrefix = "GROVIKA_";
  if (!device_id || std::strncmp(device_id, kPrefix, std::strlen(kPrefix)) != 0) {
    return false;
  }
  const char* suffix = device_id + std::strlen(kPrefix);
  if (std::strlen(suffix) != 6) {
    return false;
  }
  for (size_t i = 0; i < 6; ++i) {
    if (!std::isdigit(static_cast<unsigned char>(suffix[i]))
        && !(suffix[i] >= 'A' && suffix[i] <= 'F')) {
      return false;
    }
  }
  return true;
}

bool IsPasswordValid(const char* password) {
  if (!password || std::strlen(password) != 43) {
    return false;
  }
  for (size_t i = 0; password[i] != '\0'; ++i) {
    const char value = password[i];
    if (!std::isalnum(static_cast<unsigned char>(value)) && value != '-' && value != '_') {
      return false;
    }
  }
  return true;
}
}

MqttCredentialLoadResult MqttCredentialConfigStore::Load(StorageService* storage,
                                                          const char* expected_device_id,
                                                          MqttCredentialConfig* out) {
  if (!storage || !out) {
    return MqttCredentialLoadResult::kStorageUnavailable;
  }
  if (!storage->Exists(kMqttConfigPath)) {
    return MqttCredentialLoadResult::kMissing;
  }
  char json[256];
  if (!storage->ReadFile(kMqttConfigPath, json, sizeof(json))) {
    return MqttCredentialLoadResult::kReadFailed;
  }
  MqttCredentialConfig parsed{};
  if (!Decode(json, &parsed)) {
    return MqttCredentialLoadResult::kInvalid;
  }
  if (!expected_device_id || std::strcmp(parsed.device_id, expected_device_id) != 0) {
    return MqttCredentialLoadResult::kDeviceIdMismatch;
  }
  *out = parsed;
  return MqttCredentialLoadResult::kOk;
}

bool MqttCredentialConfigStore::Decode(const char* json, MqttCredentialConfig* out) {
  if (!json || !out) {
    return false;
  }
  unsigned long schema_version = 0;
  MqttCredentialConfig parsed{};
  if (!HasObjectEnvelope(json)
      || !HasSingleKey(json, "schema_version")
      || !HasSingleKey(json, "device_id")
      || !HasSingleKey(json, "password")
      || !ExtractUint(json, "schema_version", &schema_version) || schema_version != 1
      || !ExtractString(json, "device_id", parsed.device_id, sizeof(parsed.device_id))
      || !ExtractString(json, "password", parsed.password, sizeof(parsed.password))
      || !IsDeviceIdValid(parsed.device_id)
      || !IsPasswordValid(parsed.password)) {
    return false;
  }
  *out = parsed;
  return true;
}

const char* MqttCredentialConfigStore::Reason(MqttCredentialLoadResult result) {
  switch (result) {
    case MqttCredentialLoadResult::kOk:
      return "configured";
    case MqttCredentialLoadResult::kStorageUnavailable:
      return "storage unavailable";
    case MqttCredentialLoadResult::kMissing:
      return "mqtt.json missing";
    case MqttCredentialLoadResult::kReadFailed:
      return "mqtt.json read failed";
    case MqttCredentialLoadResult::kInvalid:
      return "mqtt.json invalid";
    case MqttCredentialLoadResult::kDeviceIdMismatch:
      return "mqtt.json device_id mismatch";
    default:
      return "mqtt.json invalid";
  }
}

}
