/*
 * Chto v faile: struktura i zagruzka seriynoy MQTT konfiguracii.
 * Rol v arhitekture: services.
 * Naznachenie: validaciya /cfg/mqtt.json bez raskrytiya sekreta.
 */

#pragma once

#include <cstddef>

namespace Services {

class StorageService;

enum class MqttCredentialLoadResult {
  kOk = 0,
  kStorageUnavailable,
  kMissing,
  kReadFailed,
  kInvalid,
  kDeviceIdMismatch
};

struct MqttCredentialConfig {
  char device_id[32];
  char password[64];
};

class MqttCredentialConfigStore {
 public:
  static MqttCredentialLoadResult Load(StorageService* storage,
                                       const char* expected_device_id,
                                       MqttCredentialConfig* out);
  static bool Decode(const char* json, MqttCredentialConfig* out);
  static const char* Reason(MqttCredentialLoadResult result);
};

}
