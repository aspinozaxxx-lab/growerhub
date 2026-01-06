#include <cstdint>
#include <ctime>

#include <unity.h>

#include "drivers/rtc/Ds3231Driver.h"

void test_rtc_bcd_encode_decode() {
  uint8_t bcd = 0;
  uint8_t decoded = 0;

  TEST_ASSERT_TRUE(Drivers::Ds3231Driver::EncodeBcdForTests(0, &bcd));
  TEST_ASSERT_EQUAL_UINT8(0x00, bcd);
  TEST_ASSERT_TRUE(Drivers::Ds3231Driver::DecodeBcdForTests(bcd, &decoded));
  TEST_ASSERT_EQUAL_UINT8(0, decoded);

  TEST_ASSERT_TRUE(Drivers::Ds3231Driver::EncodeBcdForTests(59, &bcd));
  TEST_ASSERT_EQUAL_UINT8(0x59, bcd);
  TEST_ASSERT_TRUE(Drivers::Ds3231Driver::DecodeBcdForTests(bcd, &decoded));
  TEST_ASSERT_EQUAL_UINT8(59, decoded);
}

void test_rtc_weekday_calc() {
  TEST_ASSERT_EQUAL_UINT8(4, Drivers::Ds3231Driver::CalcWeekdayForTests(static_cast<std::time_t>(0)));
  TEST_ASSERT_EQUAL_UINT8(7, Drivers::Ds3231Driver::CalcWeekdayForTests(static_cast<std::time_t>(3 * 86400)));
  TEST_ASSERT_EQUAL_UINT8(1, Drivers::Ds3231Driver::CalcWeekdayForTests(static_cast<std::time_t>(4 * 86400)));
}
