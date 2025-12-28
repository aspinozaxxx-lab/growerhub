#include <cstdio>
#include <unity.h>

#include "core/Context.h"
#include "services/StorageService.h"
#include "services/WiFiService.h"

static void CleanupWifiStorage() {
  std::remove("test2/tmp/test_storage_wifi/cfg/wifi.json");
  std::remove("test2/tmp/test_storage_wifi/cfg");
  std::remove("test2/tmp/test_storage_wifi");
}

void test_wifi_defaults_builtin() {
  CleanupWifiStorage();

  Core::Context ctx{};
  Services::StorageService storage;
  Services::WiFiService wifi;
  ctx.storage = &storage;

  storage.SetRootForTests("test2/tmp/test_storage_wifi");
  storage.Init(ctx);
  wifi.Init(ctx);

  Services::WiFiNetworkList networks = wifi.GetPreferredNetworks();
  TEST_ASSERT_EQUAL_UINT(5, static_cast<unsigned int>(networks.count));
  TEST_ASSERT_EQUAL_STRING("JR", networks.entries[0].ssid);
  TEST_ASSERT_EQUAL_STRING("qazwsxedc", networks.entries[0].password);
}

void test_wifi_defaults_user_override() {
  CleanupWifiStorage();

  Core::Context ctx{};
  Services::StorageService storage;
  Services::WiFiService wifi;
  ctx.storage = &storage;

  storage.SetRootForTests("test2/tmp/test_storage_wifi");
  storage.Init(ctx);

  const char* payload =
      "{\"schema_version\":1,\"networks\":[{\"ssid\":\"HomeWiFi\",\"password\":\"homepass\"},{\"ssid\":\"OfficeWiFi\",\"password\":\"officepass\"}]}";
  TEST_ASSERT_TRUE(storage.WriteFileAtomic("/cfg/wifi.json", payload));

  wifi.Init(ctx);

  Services::WiFiNetworkList networks = wifi.GetPreferredNetworks();
  TEST_ASSERT_EQUAL_UINT(2, static_cast<unsigned int>(networks.count));
  TEST_ASSERT_EQUAL_STRING("HomeWiFi", networks.entries[0].ssid);
  TEST_ASSERT_EQUAL_STRING("homepass", networks.entries[0].password);
  TEST_ASSERT_EQUAL_STRING("OfficeWiFi", networks.entries[1].ssid);
  TEST_ASSERT_EQUAL_STRING("officepass", networks.entries[1].password);
}
