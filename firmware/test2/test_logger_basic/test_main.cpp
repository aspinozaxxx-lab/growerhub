#include <unity.h>
#include "Logging/Logger.h"

// Pustye setUp/tearDown dlya Unity.
void setUp() {}
void tearDown() {}

// Bazovyi test: vyzovy loggera ne dolzhny padat.
static void test_logger_basic() {
  Logging::Logger::Init();
  Logging::Logger::Info("test v2");
  TEST_ASSERT_TRUE(true);
}

int main(int argc, char** argv) {
  UNITY_BEGIN();
  RUN_TEST(test_logger_basic);
  return UNITY_END();
}