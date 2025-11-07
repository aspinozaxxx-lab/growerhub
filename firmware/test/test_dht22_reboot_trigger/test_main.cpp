#include <unity.h>
#include "System/Dht22RebootHelper.h"
#include "System/SystemMonitor.h"
#include "FakeMillis.h"

void setUp() {}
void tearDown() {}

namespace {

constexpr uint8_t THRESHOLD = 3;
constexpr unsigned long COOLDOWN_MS = 300000UL;

void advanceMillis(unsigned long delta) {
    FakeMillis::advance(delta);
}

void setMillis(unsigned long value) {
    FakeMillis::set(value);
}

void test_trigger_after_three_failures() {
    uint8_t fails = 0;
    unsigned long lastReboot = 0;
    setMillis(0);

    SystemMonitor monitor;
    monitor.setPumpStatusProvider([]() { return false; });
    monitor.setAckPublisher([](const String&, const char*, bool) {});
    monitor.setStatePublisher([](bool) {});
    monitor.clearRebootFlagForTests();

    for (int i = 0; i < 3; ++i) {
        const auto decision = Dht22RebootHelper::evaluateSample(
            fails,
            lastReboot,
            false,
            FakeMillis::get(),
            THRESHOLD,
            COOLDOWN_MS);
        if (i < 2) {
            TEST_ASSERT_EQUAL(Dht22RebootDecision::None, decision);
        } else {
            TEST_ASSERT_EQUAL(Dht22RebootDecision::ReadyToTrigger, decision);
            TEST_ASSERT_TRUE(monitor.rebootIfSafe(String("dht22_error"), String("")));
            lastReboot = FakeMillis::get();
            fails = 0;
        }
        advanceMillis(100);
    }

    TEST_ASSERT_TRUE(monitor.wasRebootCalledForTests());
    TEST_ASSERT_EQUAL_UINT32(0, fails);
    TEST_ASSERT_NOT_EQUAL(0UL, lastReboot);
}

void test_cooldown_blocks_second_attempt() {
    uint8_t fails = 0;
    unsigned long lastReboot = 1000;
    setMillis(2000);

    const auto decisionCooldown = Dht22RebootHelper::evaluateSample(
        fails,
        lastReboot,
        false,
        FakeMillis::get(),
        THRESHOLD,
        COOLDOWN_MS);
    TEST_ASSERT_EQUAL(Dht22RebootDecision::None, decisionCooldown);

    fails = THRESHOLD;
    const auto secondDecision = Dht22RebootHelper::evaluateSample(
        fails,
        lastReboot,
        false,
        FakeMillis::get(),
        THRESHOLD,
        COOLDOWN_MS);
    TEST_ASSERT_EQUAL(Dht22RebootDecision::CooldownActive, secondDecision);
}

void test_declined_reboot_keeps_counter() {
    uint8_t fails = 0;
    unsigned long lastReboot = 0;
    setMillis(0);

    SystemMonitor monitor;
    monitor.setPumpStatusProvider([]() { return true; });
    monitor.setAckPublisher([](const String&, const char*, bool) {});
    monitor.setStatePublisher([](bool) {});
    monitor.clearRebootFlagForTests();

    for (int i = 0; i < THRESHOLD; ++i) {
        const auto decision = Dht22RebootHelper::evaluateSample(
            fails,
            lastReboot,
            false,
            FakeMillis::get(),
            THRESHOLD,
            COOLDOWN_MS);
        advanceMillis(50);
        if (decision == Dht22RebootDecision::ReadyToTrigger) {
            TEST_ASSERT_FALSE(monitor.rebootIfSafe(String("dht22_error"), String("")));
            TEST_ASSERT_EQUAL_UINT8(THRESHOLD, fails);
            TEST_ASSERT_EQUAL(0UL, lastReboot);
        }
    }

    TEST_ASSERT_FALSE(monitor.wasRebootCalledForTests());
}

} // namespace

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_trigger_after_three_failures);
    RUN_TEST(test_cooldown_blocks_second_attempt);
    RUN_TEST(test_declined_reboot_keeps_counter);
    return UNITY_END();
}
