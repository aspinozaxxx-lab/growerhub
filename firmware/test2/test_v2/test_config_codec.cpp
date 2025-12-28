#include <unity.h>

#include "util/JsonUtil.h"

void test_config_codec() {
  Util::ConfigStub config{1};
  char buffer[8];

  TEST_ASSERT_TRUE(Util::EncodeConfig(config, buffer, sizeof(buffer)));
  TEST_ASSERT_EQUAL_STRING("{}", buffer);

  Util::ConfigStub decoded{0};
  TEST_ASSERT_TRUE(Util::DecodeConfig("{}", &decoded));
  TEST_ASSERT_TRUE(Util::ValidateConfig(decoded));

  Util::ConfigStub bad{0};
  TEST_ASSERT_FALSE(Util::ValidateConfig(bad));
}
