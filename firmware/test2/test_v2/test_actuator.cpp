#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "modules/ActuatorModule.h"

void test_pump_timeout() {
  Modules::ActuatorModule actuator;
  Config::HardwareProfile hw = Config::GetHardwareProfile();
  hw.pump_max_runtime_ms = 1000;

  Core::Context ctx{nullptr, nullptr, nullptr, nullptr, &actuator, nullptr, nullptr, nullptr, &hw};
  actuator.Init(ctx);

  TEST_ASSERT_TRUE(actuator.StartPump(5, "p1"));
  actuator.OnTick(ctx, 0);
  actuator.OnTick(ctx, 500);
  TEST_ASSERT_TRUE(actuator.IsPumpRunning());

  actuator.OnTick(ctx, 1500);
  TEST_ASSERT_FALSE(actuator.IsPumpRunning());
}

void test_light_state() {
  Modules::ActuatorModule actuator;
  Config::HardwareProfile hw = Config::GetHardwareProfile();
  Core::Context ctx{nullptr, nullptr, nullptr, nullptr, &actuator, nullptr, nullptr, nullptr, &hw};
  actuator.Init(ctx);

  actuator.SetLight(true);
  TEST_ASSERT_TRUE(actuator.IsLightOn());
  actuator.SetLight(false);
  TEST_ASSERT_FALSE(actuator.IsLightOn());
}
