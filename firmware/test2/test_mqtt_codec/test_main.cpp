#include <cstring>
#include <unity.h>

#include "util/MqttCodec.h"

void setUp() {}
void tearDown() {}

static void test_parse_pump_start() {
  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  const bool ok = Util::ParseCommand(
      "{\"type\":\"pump.start\",\"duration_s\":10,\"correlation_id\":\"c1\"}",
      command, error);

  TEST_ASSERT_TRUE(ok);
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Util::CommandType::kPumpStart), static_cast<int>(command.type));
  TEST_ASSERT_EQUAL_UINT32(10, command.duration_s);
  TEST_ASSERT_EQUAL_STRING("c1", command.correlation_id);
}

static void test_parse_pump_stop() {
  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  const bool ok = Util::ParseCommand(
      "{\"type\":\"pump.stop\",\"correlation_id\":\"c2\"}",
      command, error);

  TEST_ASSERT_TRUE(ok);
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Util::CommandType::kPumpStop), static_cast<int>(command.type));
  TEST_ASSERT_EQUAL_STRING("c2", command.correlation_id);
}

static void test_parse_reboot() {
  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  const bool ok = Util::ParseCommand(
      "{\"type\":\"reboot\",\"correlation_id\":\"r1\"}",
      command, error);

  TEST_ASSERT_TRUE(ok);
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Util::CommandType::kReboot), static_cast<int>(command.type));
  TEST_ASSERT_EQUAL_STRING("r1", command.correlation_id);
}

static void test_parse_invalid_json() {
  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  const bool ok = Util::ParseCommand("nope", command, error);

  TEST_ASSERT_FALSE(ok);
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Util::ParseError::kInvalidJson), static_cast<int>(error));
}

static void test_parse_missing_type() {
  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  const bool ok = Util::ParseCommand("{\"duration_s\":5}", command, error);

  TEST_ASSERT_FALSE(ok);
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Util::ParseError::kTypeMissing), static_cast<int>(error));
}

static void test_parse_invalid_duration() {
  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  const bool ok = Util::ParseCommand("{\"type\":\"pump.start\",\"duration_s\":0}", command, error);

  TEST_ASSERT_FALSE(ok);
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Util::ParseError::kDurationMissingOrInvalid), static_cast<int>(error));
}

static void test_ack_payloads() {
  char payload[160];

  TEST_ASSERT_TRUE(Util::BuildAckStatus("c3", "accepted", "running", payload, sizeof(payload)));
  TEST_ASSERT_EQUAL_STRING(
      "{\"correlation_id\":\"c3\",\"result\":\"accepted\",\"status\":\"running\"}",
      payload);

  TEST_ASSERT_TRUE(Util::BuildAckError("c4", "bad command format: duration_s missing or invalid",
                                      payload, sizeof(payload)));
  TEST_ASSERT_EQUAL_STRING(
      "{\"correlation_id\":\"c4\",\"result\":\"error\",\"reason\":\"bad command format: duration_s missing or invalid\"}",
      payload);
}

int main(int argc, char** argv) {
  (void)argc;
  (void)argv;
  UNITY_BEGIN();
  RUN_TEST(test_parse_pump_start);
  RUN_TEST(test_parse_pump_stop);
  RUN_TEST(test_parse_reboot);
  RUN_TEST(test_parse_invalid_json);
  RUN_TEST(test_parse_missing_type);
  RUN_TEST(test_parse_invalid_duration);
  RUN_TEST(test_ack_payloads);
  return UNITY_END();
}
