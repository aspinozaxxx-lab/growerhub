#include <cstdio>
#include <cstring>
#include <unity.h>

#include "core/Context.h"
#include "services/StorageService.h"
#include "services/WiFiService.h"
#include "services/WebConfigService.h"
#include "services/website/WebsiteContent.h"
#include "util/JsonUtil.h"

static void CleanupWifiConfigStorage() {
  std::remove("test/tmp/test_storage_wifi_config/cfg/wifi.json");
  std::remove("test/tmp/test_storage_wifi_config/cfg");
  std::remove("test/tmp/test_storage_wifi_config");
}

void test_wifi_config_codec_storage() {
  CleanupWifiConfigStorage();

  Core::Context ctx{};
  Services::StorageService storage;
  ctx.storage = &storage;

  storage.SetRootForTests("test/tmp/test_storage_wifi_config");
  storage.Init(ctx);

  const char* ssids[] = {"HomeWiFi", "OfficeWiFi"};
  const char* passwords[] = {"homepass", "officepass"};
  char json[512];
  TEST_ASSERT_TRUE(Util::EncodeWifiConfig(ssids, passwords, 2, json, sizeof(json)));
  TEST_ASSERT_TRUE(storage.WriteFileAtomic("/cfg/wifi.json", json));

  char stored[512];
  TEST_ASSERT_TRUE(storage.ReadFile("/cfg/wifi.json", stored, sizeof(stored)));
  TEST_ASSERT_EQUAL_STRING(json, stored);
  TEST_ASSERT_TRUE(Util::ValidateWifiConfig(stored));
}

void test_wifi_config_codec_invalid() {
  const char* bad_json = "{\"schema_version\":2,\"networks\":[{\"ssid\":\"\"}]}";
  TEST_ASSERT_FALSE(Util::ValidateWifiConfig(bad_json));
  const char* empty_json = "{\"schema_version\":1,\"networks\":[{\"ssid\":\"\"}]}";
  TEST_ASSERT_FALSE(Util::ValidateWifiConfig(empty_json));
  const char* valid_json = "{\"schema_version\":1,\"networks\":[{\"ssid\":\"\"},{\"ssid\":\"Ok\"}]}";
  TEST_ASSERT_TRUE(Util::ValidateWifiConfig(valid_json));
  char json[64];
  TEST_ASSERT_FALSE(Util::EncodeWifiConfig("", "pass", json, sizeof(json)));
}

void test_wifi_config_encode_decode_list() {
  const char* ssids[] = {"Net1", "Net2", "Net3"};
  const char* passwords[] = {"pass1", "pass2", "pass3"};
  char json[512];
  TEST_ASSERT_TRUE(Util::EncodeWifiConfig(ssids, passwords, 3, json, sizeof(json)));

  Services::WiFiNetworkList list{};
  TEST_ASSERT_TRUE(Services::WiFiService::ParseWifiConfig(json, list));
  TEST_ASSERT_EQUAL_UINT(3, static_cast<unsigned int>(list.count));
  TEST_ASSERT_EQUAL_STRING("Net1", list.entries[0].ssid);
  TEST_ASSERT_EQUAL_STRING("pass1", list.entries[0].password);
  TEST_ASSERT_EQUAL_STRING("Net2", list.entries[1].ssid);
  TEST_ASSERT_EQUAL_STRING("pass2", list.entries[1].password);
  TEST_ASSERT_EQUAL_STRING("Net3", list.entries[2].ssid);
  TEST_ASSERT_EQUAL_STRING("pass3", list.entries[2].password);
}

void test_webconfig_build_json() {
  char json[256];
  TEST_ASSERT_TRUE(Services::WebConfigService::BuildWifiConfigJson("Net", "secret", json, sizeof(json)));
  TEST_ASSERT_EQUAL_STRING(
      "{\"schema_version\":1,\"networks\":[{\"ssid\":\"Net\",\"password\":\"secret\"}]}",
      json);
}

void test_webconfig_shows_builtin_networks_without_user_config() {
  CleanupWifiConfigStorage();

  Core::Context ctx{};
  Services::StorageService storage;
  Services::WiFiService wifi;
  Services::WebConfigService web_config;
  ctx.storage = &storage;
  ctx.wifi = &wifi;

  storage.SetRootForTests("test/tmp/test_storage_wifi_config");
  storage.Init(ctx);
  wifi.Init(ctx);
  web_config.Init(ctx);

  const Services::WiFiNetworkList networks = web_config.GetNetworksForTests();
  TEST_ASSERT_EQUAL_UINT(5, static_cast<unsigned int>(networks.count));
  TEST_ASSERT_EQUAL_STRING("JR", networks.entries[0].ssid);
}

void test_webconfig_content_is_russian() {
  const char* html = Services::Website::Html();
  const char* js = Services::Website::Js();

  TEST_ASSERT_NOT_NULL(std::strstr(html, "Настройка Wi-Fi"));
  TEST_ASSERT_NOT_NULL(std::strstr(html, "Состояние"));
  TEST_ASSERT_NOT_NULL(std::strstr(html, "Пароль сети"));
  TEST_ASSERT_NOT_NULL(std::strstr(html, "Сохранённые сети"));
  TEST_ASSERT_NOT_NULL(std::strstr(js, "Сохранённых сетей нет"));
  TEST_ASSERT_NOT_NULL(std::strstr(js, "Удалить"));
  TEST_ASSERT_NULL(std::strstr(html, "Wi-Fi setup"));
  TEST_ASSERT_NULL(std::strstr(js, "No networks"));
}
