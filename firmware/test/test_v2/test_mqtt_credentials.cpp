#include <cstdio>
#include <cstring>
#include <unity.h>

#include "core/Context.h"
#include "services/MqttCredentialConfig.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/TimeService.h"
#include "services/Topics.h"
#include "util/Logger.h"

namespace {
const char* kRoot = "test/tmp/test_storage_mqtt_credentials";
const char* kDeviceId = "GROVIKA_040AB1";
const char* kPassword = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

void CleanupMqttStorage() {
  std::remove("test/tmp/test_storage_mqtt_credentials/cfg/mqtt.json");
  std::remove("test/tmp/test_storage_mqtt_credentials/cfg");
  std::remove("test/tmp/test_storage_mqtt_credentials");
}

Core::Context BuildContext(Services::StorageService* storage,
                           Services::TimeService* time_service,
                           Services::MqttService* mqtt) {
  Core::Context ctx{};
  ctx.storage = storage;
  ctx.time = time_service;
  ctx.mqtt = mqtt;
  ctx.device_id = kDeviceId;
  return ctx;
}
}

void test_mqtt_config_missing_and_invalid() {
  CleanupMqttStorage();
  Services::StorageService storage;
  Services::TimeService time_service;
  Services::MqttService missing_mqtt;
  Core::Context ctx = BuildContext(&storage, &time_service, &missing_mqtt);
  storage.SetRootForTests(kRoot);
  storage.Init(ctx);

  missing_mqtt.Init(ctx);
  TEST_ASSERT_FALSE(missing_mqtt.IsConfiguredForTests());
  TEST_ASSERT_EQUAL_STRING("NOT_CONFIGURED", missing_mqtt.GetStatusName());
  TEST_ASSERT_EQUAL_STRING("mqtt.json missing", missing_mqtt.GetStatusReason());

  TEST_ASSERT_TRUE(storage.WriteFileAtomic(
      "/cfg/mqtt.json",
      "{\"schema_version\":1,\"device_id\":\"GROVIKA_040AB1\",\"password\":\"short\"}"));
  Services::MqttService invalid_mqtt;
  ctx.mqtt = &invalid_mqtt;
  invalid_mqtt.Init(ctx);
  TEST_ASSERT_FALSE(invalid_mqtt.IsConfiguredForTests());
  TEST_ASSERT_EQUAL_STRING("mqtt.json invalid", invalid_mqtt.GetStatusReason());
}

void test_mqtt_config_tls_time_gate_and_secret_free_log() {
  CleanupMqttStorage();
  Services::StorageService storage;
  Services::TimeService time_service;
  Services::MqttService mqtt;
  Core::Context ctx = BuildContext(&storage, &time_service, &mqtt);
  storage.SetRootForTests(kRoot);
  storage.Init(ctx);

  char json[192];
  std::snprintf(json,
                sizeof(json),
                "{\"schema_version\":1,\"device_id\":\"%s\",\"password\":\"%s\"}",
                kDeviceId,
                kPassword);
  TEST_ASSERT_TRUE(storage.WriteFileAtomic("/cfg/mqtt.json", json));

  Util::Logger::ClearLastMessageForTests();
  mqtt.Init(ctx);
  TEST_ASSERT_TRUE(mqtt.IsConfiguredForTests());
  TEST_ASSERT_EQUAL_STRING("WAITING_WIFI", mqtt.GetStatusName());
  TEST_ASSERT_NULL(std::strstr(Util::Logger::GetLastMessageForTests(), kPassword));
  TEST_ASSERT_EQUAL_STRING("growerhub.ru", mqtt.GetHost());
  TEST_ASSERT_EQUAL_UINT16(8883, mqtt.GetPort());
  TEST_ASSERT_TRUE(mqtt.IsTls());

  mqtt.SetWifiReady(true);
  TEST_ASSERT_EQUAL_STRING("WAITING_TIME", mqtt.GetStatusName());
  Services::TimeFields fields{2026, 8, 1, 12, 0, 0, 6};
  time_service.SetTimeForTests(fields, 1785585600000ULL);
  mqtt.SetWifiReady(true);
  TEST_ASSERT_EQUAL_STRING("CONNECTING", mqtt.GetStatusName());
}

void test_mqtt_config_rejects_other_device_and_state_topic_is_scoped() {
  Services::MqttCredentialConfig config{};
  TEST_ASSERT_TRUE(Services::MqttCredentialConfigStore::Decode(
      "{\"schema_version\":1,\"device_id\":\"GROVIKA_040AB1\","
      "\"password\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}",
      &config));
  TEST_ASSERT_EQUAL_STRING(kDeviceId, config.device_id);
  TEST_ASSERT_FALSE(Services::MqttCredentialConfigStore::Decode(
      "{\"schema_version\":1,\"device_id\":\"GROVIKA_040AB1\","
      "\"password\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"} trailing",
      &config));
  TEST_ASSERT_FALSE(Services::MqttCredentialConfigStore::Decode(
      "{\"schema_version\":1,\"schema_version\":1,\"device_id\":\"GROVIKA_040AB1\","
      "\"password\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}",
      &config));

  CleanupMqttStorage();
  Services::StorageService storage;
  Core::Context ctx{};
  storage.SetRootForTests(kRoot);
  storage.Init(ctx);
  TEST_ASSERT_TRUE(storage.WriteFileAtomic(
      "/cfg/mqtt.json",
      "{\"schema_version\":1,\"device_id\":\"GROVIKA_000001\","
      "\"password\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"));
  Services::MqttCredentialConfig loaded{};
  TEST_ASSERT_EQUAL_INT(
      static_cast<int>(Services::MqttCredentialLoadResult::kDeviceIdMismatch),
      static_cast<int>(Services::MqttCredentialConfigStore::Load(&storage, kDeviceId, &loaded)));

  char topic[64];
  TEST_ASSERT_TRUE(Services::Topics::BuildStateTopic(topic, sizeof(topic), kDeviceId));
  TEST_ASSERT_EQUAL_STRING("gh/dev/GROVIKA_040AB1/state", topic);
  TEST_ASSERT_NULL(std::strstr(topic, "GROVIKA_000001"));
}
