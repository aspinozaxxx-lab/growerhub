#include <cstdio>
#include <unity.h>

#include "core/Context.h"
#include "services/StorageService.h"
#include "services/WebConfigService.h"
#include "util/JsonUtil.h"

static void CleanupWifiConfigStorage() {
  std::remove("test_storage_wifi_config/cfg/wifi.json");
  std::remove("test_storage_wifi_config/cfg");
  std::remove("test_storage_wifi_config");
}

void test_wifi_config_codec_storage() {
  CleanupWifiConfigStorage();

  Core::Context ctx{};
  Services::StorageService storage;
  ctx.storage = &storage;

  storage.SetRootForTests("test_storage_wifi_config");
  storage.Init(ctx);

  char json[256];
  TEST_ASSERT_TRUE(Util::EncodeWifiConfig("HomeWiFi", "homepass", json, sizeof(json)));
  TEST_ASSERT_TRUE(storage.WriteFileAtomic("/cfg/wifi.json", json));

  char stored[256];
  TEST_ASSERT_TRUE(storage.ReadFile("/cfg/wifi.json", stored, sizeof(stored)));
  TEST_ASSERT_TRUE(Util::ValidateWifiConfig(stored));
}

void test_wifi_config_codec_invalid() {
  const char* bad_json = "{\"schema_version\":2,\"networks\":[{\"ssid\":\"\"}]}";
  TEST_ASSERT_FALSE(Util::ValidateWifiConfig(bad_json));
  char json[64];
  TEST_ASSERT_FALSE(Util::EncodeWifiConfig("", "pass", json, sizeof(json)));
}

void test_webconfig_build_json() {
  char json[256];
  TEST_ASSERT_TRUE(Services::WebConfigService::BuildWifiConfigJson("Net", "secret", json, sizeof(json)));
  TEST_ASSERT_EQUAL_STRING(
      "{\"schema_version\":1,\"networks\":[{\"ssid\":\"Net\",\"password\":\"secret\"}]}",
      json);
}
