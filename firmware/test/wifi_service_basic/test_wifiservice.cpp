#include <unity.h>
#include "Network/WiFiService.h"
#include "../fakes/FakeWiFiSettings.h"
#include "Network/WiFiShim.h"

static FakeWiFiSettings makeSettings(std::initializer_list<std::pair<const char*, const char*>> list) {
    FakeWiFiSettings settings;
    for (const auto& item : list) {
        settings.addCredential(item.first, item.second ? item.second : "");
    }
    return settings;
}

void setUp() {
    FakeWiFiShim::reset();
}

void tearDown() {}

void test_connect_sync_without_credentials() {
    auto settings = makeSettings({});
    WiFiService service(settings);

    FakeWiFiShim::setInitialStatus(WL_DISCONNECTED);
    FakeWiFiShim::setMillis(0);

    TEST_ASSERT_FALSE(service.connectSync());
    TEST_ASSERT_FALSE(service.isOnline());
    TEST_ASSERT_FALSE(service.canTransmit());
    TEST_ASSERT_EQUAL_UINT32(0, FakeWiFiShim::getRunCallCount());
}

void test_connect_sync_with_single_credential_success() {
    auto settings = makeSettings({{"TestAP", "pwd"}});
    WiFiService service(settings);

    FakeWiFiShim::setInitialStatus(WL_DISCONNECTED);
    FakeWiFiShim::setConnectionResultSequence({WL_CONNECTED});
    FakeWiFiShim::setNetworkIdentity("TestAP", "192.168.1.10");
    FakeWiFiShim::setMillis(0);

    TEST_ASSERT_TRUE(service.connectSync());
    TEST_ASSERT_TRUE(service.isOnline());
    TEST_ASSERT_TRUE(service.canTransmit());
    TEST_ASSERT_EQUAL_UINT32(1, FakeWiFiShim::getRunCallCount());
}

void test_retry_and_backoff_sequence() {
    auto settings = makeSettings({{"AP1", "pwd1"}, {"AP2", "pwd2"}});
    WiFiService service(settings);

    FakeWiFiShim::setInitialStatus(WL_DISCONNECTED);
    FakeWiFiShim::setConnectionResultSequence({WL_CONNECT_FAILED, WL_CONNECT_FAILED, WL_CONNECTED});
    FakeWiFiShim::setNetworkIdentity("AP1", "10.0.0.5");

    FakeWiFiShim::setMillis(0);
    TEST_ASSERT_FALSE(service.connectSync());
    TEST_ASSERT_FALSE(service.isOnline());
    TEST_ASSERT_EQUAL_UINT32(1, FakeWiFiShim::getRunCallCount());

    service.startAsyncReconnectIfNeeded();

    FakeWiFiShim::setMillis(4000);
    service.loop(4000);
    TEST_ASSERT_EQUAL_UINT32(1, FakeWiFiShim::getRunCallCount());
    TEST_ASSERT_FALSE(service.isOnline());

    FakeWiFiShim::setMillis(6000);
    service.loop(6000);
    TEST_ASSERT_EQUAL_UINT32(2, FakeWiFiShim::getRunCallCount());
    TEST_ASSERT_FALSE(service.isOnline());

    FakeWiFiShim::setMillis(25000);
    service.loop(25000);
    TEST_ASSERT_EQUAL_UINT32(2, FakeWiFiShim::getRunCallCount());

    FakeWiFiShim::setMillis(26000);
    service.loop(26000);
    TEST_ASSERT_EQUAL_UINT32(3, FakeWiFiShim::getRunCallCount());
    TEST_ASSERT_TRUE(service.isOnline());
    TEST_ASSERT_TRUE(service.canTransmit());
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_connect_sync_without_credentials);
    RUN_TEST(test_connect_sync_with_single_credential_success);
    RUN_TEST(test_retry_and_backoff_sequence);
    return UNITY_END();
}

