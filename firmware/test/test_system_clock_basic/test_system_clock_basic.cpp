#include <unity.h>
#include "System/SystemClock.h"
#include "../fakes/FakeRTC.h"
#include "../fakes/FakeNTPClient.h"
#include "../fakes/FakeScheduler.h"
#include "../fakes/FakeMillis.h"

namespace {
    constexpr time_t TS_2024 = 1703980800; // 2024-12-31 00:00:00 UTC
    constexpr time_t TS_2025 = 1735689600; // 2025-12-31 00:00:00 UTC
    constexpr time_t TS_2026 = 1767225600; // 2026-01-01 00:00:00 UTC
    constexpr time_t TS_2040 = 2240582400; // 2040-12-31 00:00:00 UTC
    constexpr time_t TS_2041 = 2272300800; // 2041-12-31 00:00:00 UTC

    void resetTime() {
        FakeMillis::set(0);
    }
}

void setUp() {
    resetTime();
}

void tearDown() {}

void test_fallback_without_time() {
    FakeRTC rtc;
    rtc.setValid(false);
    FakeNTPClient ntp;
    FakeScheduler scheduler;
    SystemClock clock(&rtc, &ntp, nullptr, &scheduler);

    clock.begin();

    TEST_ASSERT_FALSE(clock.isTimeSet());
    time_t value = 0;
    TEST_ASSERT_FALSE(clock.nowUtc(value));
    TEST_ASSERT_EQUAL_STRING("1970-01-01T00:00:00Z", clock.formatIso8601(0).c_str());
}

void test_successful_start_sync() {
    FakeRTC rtc;
    FakeNTPClient ntp;
    ntp.enqueueResult(false, 0);
    ntp.enqueueResult(true, TS_2026);
    FakeScheduler scheduler;
    SystemClock clock(&rtc, &ntp, nullptr, &scheduler);

    clock.begin();

    TEST_ASSERT_TRUE(clock.isTimeSet());
    time_t value = 0;
    TEST_ASSERT_TRUE(clock.nowUtc(value));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026), static_cast<uint32_t>(value));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026), static_cast<uint32_t>(rtc.lastSetTime()));
}

void test_suspicious_ntp_rejected() {
    FakeRTC rtc;
    rtc.setValid(true);
    rtc.setCurrent(TS_2026);
    FakeNTPClient ntp;
    ntp.enqueueResult(true, TS_2026 + 40LL * 24 * 3600);
    FakeScheduler scheduler;
    SystemClock clock(&rtc, &ntp, nullptr, &scheduler);

    clock.begin();

    TEST_ASSERT_TRUE(clock.isTimeSet());
    time_t value = 0;
    TEST_ASSERT_TRUE(clock.nowUtc(value));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026), static_cast<uint32_t>(value));

    time_t rtcValue = 0;
    TEST_ASSERT_TRUE(rtc.getTime(rtcValue));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026), static_cast<uint32_t>(rtcValue));
}

void test_retry_every_30_seconds() {
    FakeRTC rtc;
    FakeNTPClient ntp;
    ntp.enqueueResult(false, 0);
    ntp.enqueueResult(false, 0);
    ntp.enqueueResult(false, 0);
    FakeScheduler scheduler;
    SystemClock clock(&rtc, &ntp, nullptr, &scheduler);

    clock.begin();

    TEST_ASSERT_FALSE(clock.isTimeSet());

    ntp.enqueueResult(true, TS_2026);
    scheduler.runDue(30000);

    TEST_ASSERT_TRUE(clock.isTimeSet());
    time_t value = 0;
    TEST_ASSERT_TRUE(clock.nowUtc(value));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026), static_cast<uint32_t>(value));
}

void test_resync_every_6_hours() {
    FakeRTC rtc;
    FakeNTPClient ntp;
    ntp.enqueueResult(true, TS_2026);
    ntp.enqueueResult(true, TS_2026 + 6LL * 3600);
    FakeScheduler scheduler;
    SystemClock clock(&rtc, &ntp, nullptr, &scheduler);

    clock.begin();
    TEST_ASSERT_TRUE(clock.isTimeSet());

    scheduler.runDue(6UL * 60UL * 60UL * 1000UL);

    time_t value = 0;
    TEST_ASSERT_TRUE(clock.nowUtc(value));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026 + 6LL * 3600), static_cast<uint32_t>(value));
}

void test_null_dependencies() {
    FakeRTC rtc;
    rtc.setValid(true);
    rtc.setCurrent(TS_2026);
    SystemClock clockWithRtc(&rtc, nullptr, nullptr, nullptr);
    clockWithRtc.begin();

    TEST_ASSERT_TRUE(clockWithRtc.isTimeSet());
    time_t value = 0;
    TEST_ASSERT_TRUE(clockWithRtc.nowUtc(value));
    TEST_ASSERT_EQUAL_UINT32(static_cast<uint32_t>(TS_2026), static_cast<uint32_t>(value));

    SystemClock clockNoDeps(nullptr, nullptr, nullptr, nullptr);
    clockNoDeps.begin();
    TEST_ASSERT_FALSE(clockNoDeps.isTimeSet());
}

void test_year_boundaries() {
    // 2024 (отклоняем)
    {
        FakeRTC rtc;
        FakeNTPClient ntp;
        ntp.enqueueResult(true, TS_2024);
        SystemClock clock(&rtc, &ntp, nullptr, nullptr);
        clock.begin();
        TEST_ASSERT_FALSE(clock.isTimeSet());
    }

    // 2025 (валидно)
    {
        FakeRTC rtc;
        FakeNTPClient ntp;
        ntp.enqueueResult(true, TS_2025);
        SystemClock clock(&rtc, &ntp, nullptr, nullptr);
        clock.begin();
        TEST_ASSERT_TRUE(clock.isTimeSet());
    }

    if (sizeof(time_t) < 8) {
        TEST_IGNORE_MESSAGE("time_t не поддерживает годы >2038 в тестовой среде");
        return;
    }

    // 2040 (валидно)
    {
        FakeRTC rtc;
        FakeNTPClient ntp;
        ntp.enqueueResult(true, TS_2040);
        SystemClock clock(&rtc, &ntp, nullptr, nullptr);
        clock.begin();
        TEST_ASSERT_TRUE(clock.isTimeSet());
    }

    // 2041 (отклоняем)
    {
        FakeRTC rtc;
        FakeNTPClient ntp;
        ntp.enqueueResult(true, TS_2041);
        SystemClock clock(&rtc, &ntp, nullptr, nullptr);
        clock.begin();
        TEST_ASSERT_FALSE(clock.isTimeSet());
    }
}

int main() {
    UNITY_BEGIN();
    RUN_TEST(test_fallback_without_time);
    RUN_TEST(test_successful_start_sync);
    RUN_TEST(test_suspicious_ntp_rejected);
    RUN_TEST(test_retry_every_30_seconds);
    RUN_TEST(test_resync_every_6_hours);
    RUN_TEST(test_null_dependencies);
    RUN_TEST(test_year_boundaries);
    return UNITY_END();
}
