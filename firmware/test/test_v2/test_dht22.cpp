#include <cmath>
#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "drivers/dht/Dht22Sensor.h"
#include "modules/SensorHubModule.h"

static int g_dht_read_count = 0;
static bool g_dht_nan = false;

static bool DhtReadHook(float* out_temp_c, float* out_humidity) {
  g_dht_read_count++;
  if (!out_temp_c || !out_humidity) {
    return false;
  }
  if (g_dht_nan) {
    *out_temp_c = NAN;
    *out_humidity = NAN;
  } else {
    *out_temp_c = 22.0f;
    *out_humidity = 50.0f;
  }
  return true;
}

void test_dht_rate_limit() {
  Drivers::Dht22Sensor sensor;
  sensor.Init(15);
  sensor.SetReadHook(&DhtReadHook);

  g_dht_read_count = 0;
  g_dht_nan = false;

  float temp = 0.0f;
  float humidity = 0.0f;
  TEST_ASSERT_TRUE(sensor.Read(0, &temp, &humidity));
  TEST_ASSERT_EQUAL_INT(1, g_dht_read_count);

  TEST_ASSERT_TRUE(sensor.Read(1000, &temp, &humidity));
  TEST_ASSERT_EQUAL_INT(1, g_dht_read_count);

  TEST_ASSERT_TRUE(sensor.Read(2500, &temp, &humidity));
  TEST_ASSERT_EQUAL_INT(2, g_dht_read_count);
}

void test_dht_nan_unavailable() {
  Drivers::Dht22Sensor sensor;
  sensor.Init(15);
  sensor.SetReadHook(&DhtReadHook);

  g_dht_read_count = 0;
  g_dht_nan = true;

  float temp = 0.0f;
  float humidity = 0.0f;
  TEST_ASSERT_FALSE(sensor.Read(0, &temp, &humidity));
  TEST_ASSERT_FALSE(sensor.IsAvailable());
}

void test_dht_fail_triggers_reboot_event() {
  Core::EventQueue queue;
  Modules::SensorHubModule hub;
  Config::HardwareProfile hw = Config::GetHardwareProfile();
  hw.has_dht22 = true;
  hw.dht_auto_reboot_on_fail = true;

  Core::Context ctx{nullptr, &queue, nullptr, nullptr, nullptr, nullptr, nullptr, &hub, nullptr, &hw, nullptr};
  hub.Init(ctx);

  Drivers::Dht22Sensor* dht = hub.GetDhtSensor();
  TEST_ASSERT_NOT_NULL(dht);
  dht->SetReadHook(&DhtReadHook);

  g_dht_nan = true;
  g_dht_read_count = 0;

  hub.OnTick(ctx, 0);
  hub.OnTick(ctx, 5000);
  hub.OnTick(ctx, 10000);

  Core::Event event{};
  TEST_ASSERT_TRUE(queue.Pop(event));
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Core::EventType::kRebootRequest),
                        static_cast<int>(event.type));
}
