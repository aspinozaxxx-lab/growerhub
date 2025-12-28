#include <unity.h>

#include "util/JsonUtil.h"

void test_config_codec_valid() {
  Util::ScenariosConfig config = Util::DefaultScenariosConfig();
  config.water_time_enabled = true;
  config.light_schedule_enabled = true;

  char buffer[256];
  TEST_ASSERT_TRUE(Util::EncodeScenariosConfig(config, buffer, sizeof(buffer)));

  Util::ScenariosConfig decoded{};
  TEST_ASSERT_TRUE(Util::DecodeScenariosConfig(buffer, &decoded));
  TEST_ASSERT_TRUE(Util::ValidateScenariosConfig(decoded));
  TEST_ASSERT_TRUE(decoded.water_time_enabled);
  TEST_ASSERT_TRUE(decoded.light_schedule_enabled);
  TEST_ASSERT_FALSE(decoded.water_moisture_enabled);
}

void test_config_codec_invalid() {
  Util::ScenariosConfig decoded{};
  TEST_ASSERT_FALSE(Util::DecodeScenariosConfig("", &decoded));
  TEST_ASSERT_FALSE(Util::DecodeScenariosConfig("{}", &decoded));
  TEST_ASSERT_TRUE(Util::DecodeScenariosConfig(R"({"schema_version":2})", &decoded));
  TEST_ASSERT_FALSE(Util::ValidateScenariosConfig(decoded));
}
