#include <unity.h>
#include "System/SystemMonitor.h"
#include "FakeMillis.h"

struct FakeRebooter : SystemMonitor::IRebooter {
    bool called = false;
    void restart() override {
        called = true;
    }
};

void setUp() {}
void tearDown() {}

namespace {

void commonSetup(SystemMonitor& monitor, FakeRebooter& rebooter) {
    monitor.setRebooter(&rebooter);
    monitor.clearRebootFlagForTests();
    FakeMillis::set(0);
}

void test_declined_when_pump_running() {
    SystemMonitor monitor;
    FakeRebooter rebooter;
    String lastCorrelation;
    String lastStatus;
    bool lastAccepted = true;

    monitor.setPumpStatusProvider([]() { return true; });
    monitor.setAckPublisher([&](const String& corr, const char* status, bool accepted) {
        lastCorrelation = corr;
        lastStatus = status ? String(status) : String("");
        lastAccepted = accepted;
    });
    commonSetup(monitor, rebooter);

    const bool result = monitor.rebootIfSafe(String("server_command"), String("abc"));

    TEST_ASSERT_FALSE(result);
    TEST_ASSERT_FALSE(rebooter.called);
    TEST_ASSERT_FALSE(monitor.wasRebootCalledForTests());
    TEST_ASSERT_EQUAL_STRING("abc", lastCorrelation.c_str());
    TEST_ASSERT_EQUAL_STRING("running", lastStatus.c_str());
    TEST_ASSERT_FALSE(lastAccepted);
}

void test_accepted_when_idle() {
    SystemMonitor monitor;
    FakeRebooter rebooter;
    bool statePublished = false;
    monitor.setPumpStatusProvider([]() { return false; });
    monitor.setAckPublisher([](const String&, const char*, bool) {});
    monitor.setStatePublisher([&](bool) { statePublished = true; });
    commonSetup(monitor, rebooter);

    const bool result = monitor.rebootIfSafe(String("server_command"), String("req1"));

    TEST_ASSERT_TRUE(result);
    TEST_ASSERT_TRUE(rebooter.called);
    TEST_ASSERT_TRUE(monitor.wasRebootCalledForTests());
    TEST_ASSERT_TRUE(statePublished);
}

void test_without_ack_publisher() {
    SystemMonitor monitor;
    FakeRebooter rebooter;
    monitor.setPumpStatusProvider([]() { return false; });
    commonSetup(monitor, rebooter);

    const bool result = monitor.rebootIfSafe(String("server_command"), String(""));

    TEST_ASSERT_TRUE(result);
    TEST_ASSERT_TRUE(rebooter.called);
}

void test_grace_delay_advances_time() {
    SystemMonitor monitor;
    FakeRebooter rebooter;
    monitor.setPumpStatusProvider([]() { return false; });
    commonSetup(monitor, rebooter);

    FakeMillis::set(100);
    const bool result = monitor.rebootIfSafe(String("server_command"), String("delay"));
    TEST_ASSERT_TRUE(result);
    TEST_ASSERT_TRUE(rebooter.called);
    TEST_ASSERT_TRUE(monitor.wasRebootCalledForTests());
    TEST_ASSERT_UINT_WITHIN(10, 105, FakeMillis::get());
}

} // namespace

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_declined_when_pump_running);
    RUN_TEST(test_accepted_when_idle);
    RUN_TEST(test_without_ack_publisher);
    RUN_TEST(test_grace_delay_advances_time);
    return UNITY_END();
}
