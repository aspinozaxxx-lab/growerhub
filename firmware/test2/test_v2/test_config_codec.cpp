#include <unity.h>

#include "util/JsonUtil.h"

void test_config_codec_valid() {
  Util::ScenariosConfig config = Util::DefaultScenariosConfig();
  config.water_moisture.enabled = true;
  config.water_moisture.min_time_between_watering_s = 3600;
  config.water_moisture.sensors[0].enabled = true;
  config.water_moisture.sensors[0].threshold_percent = 25;
  config.water_moisture.sensors[0].duration_s = 30;

  config.water_schedule.enabled = true;
  config.water_schedule.entry_count = 1;
  config.water_schedule.entries[0].start_hhmm = 730;
  config.water_schedule.entries[0].duration_s = 20;
  config.water_schedule.entries[0].days_mask = 0x7F;

  config.light_schedule.enabled = true;
  config.light_schedule.entry_count = 1;
  config.light_schedule.entries[0].start_hhmm = 800;
  config.light_schedule.entries[0].end_hhmm = 2100;
  config.light_schedule.entries[0].days_mask = 0x7F;

  char buffer[1024];
  TEST_ASSERT_TRUE(Util::EncodeScenariosConfig(config, buffer, sizeof(buffer)));

  Util::ScenariosConfig decoded{};
  TEST_ASSERT_TRUE(Util::DecodeScenariosConfig(buffer, &decoded));
  TEST_ASSERT_TRUE(Util::ValidateScenariosConfig(decoded));
  TEST_ASSERT_TRUE(decoded.water_moisture.enabled);
  TEST_ASSERT_EQUAL_UINT32(3600, decoded.water_moisture.min_time_between_watering_s);
  TEST_ASSERT_TRUE(decoded.water_moisture.sensors[0].enabled);
  TEST_ASSERT_EQUAL_UINT8(25, decoded.water_moisture.sensors[0].threshold_percent);
  TEST_ASSERT_EQUAL_UINT32(30, decoded.water_moisture.sensors[0].duration_s);
  TEST_ASSERT_TRUE(decoded.water_schedule.enabled);
  TEST_ASSERT_EQUAL_UINT(1, static_cast<unsigned int>(decoded.water_schedule.entry_count));
  TEST_ASSERT_TRUE(decoded.light_schedule.enabled);
  TEST_ASSERT_EQUAL_UINT(1, static_cast<unsigned int>(decoded.light_schedule.entry_count));
}

void test_config_codec_invalid() {
  Util::ScenariosConfig decoded{};
  TEST_ASSERT_FALSE(Util::DecodeScenariosConfig("", &decoded));
  TEST_ASSERT_FALSE(Util::DecodeScenariosConfig("{}", &decoded));
  TEST_ASSERT_TRUE(Util::DecodeScenariosConfig(R"({"schema_version":1})", &decoded));
  TEST_ASSERT_TRUE(Util::ValidateScenariosConfig(decoded));

  Util::ScenariosConfig invalid = Util::DefaultScenariosConfig();
  invalid.water_schedule.enabled = true;
  invalid.water_schedule.entry_count = 1;
  invalid.water_schedule.entries[0].start_hhmm = 2475;
  invalid.water_schedule.entries[0].duration_s = 10;
  invalid.water_schedule.entries[0].days_mask = 1;
  TEST_ASSERT_FALSE(Util::ValidateScenariosConfig(invalid));
}

void test_config_codec_migrate() {
  const char* old_json =
      "{\"schema_version\":1,\"scenarios\":{\"water_time\":{\"enabled\":true},\"water_moisture\":{\"enabled\":false},\"light_schedule\":{\"enabled\":true}}}";
  Util::ScenariosConfig decoded{};
  TEST_ASSERT_TRUE(Util::DecodeScenariosConfig(old_json, &decoded));
  TEST_ASSERT_TRUE(Util::ValidateScenariosConfig(decoded));
  TEST_ASSERT_TRUE(decoded.water_schedule.enabled);
  TEST_ASSERT_FALSE(decoded.water_moisture.enabled);
  TEST_ASSERT_TRUE(decoded.light_schedule.enabled);
}
