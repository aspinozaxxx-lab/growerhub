#include <cstdio>
#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"
#include "modules/ConfigSyncModule.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/Topics.h"
#include "util/JsonUtil.h"

void test_config_sync_apply_retained() {
  std::remove("test/tmp/test_storage_sync/cfg/scenarios.json");
  std::remove("test/tmp/test_storage_sync/cfg");
  std::remove("test/tmp/test_storage_sync");

  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Services::StorageService storage;
  Modules::ConfigSyncModule sync;
  const Config::HardwareProfile& hw = Config::GetHardwareProfile();
  const char* device_id = "grovika_040AB1";

  Core::Context ctx{&scheduler, &queue, &mqtt, &storage, nullptr, nullptr, &sync, nullptr, nullptr, &hw, device_id};
  storage.SetRootForTests("test/tmp/test_storage_sync");
  storage.Init(ctx);
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  sync.Init(ctx);

  char topic[128];
  Services::Topics::BuildCfgTopic(topic, sizeof(topic), device_id);

  const char* payload =
      "{\"schema_version\":2,\"watering\":{\"by_moisture\":{\"enabled\":true,\"min_time_between_watering_s\":3600,\"per_sensor\":[{\"port\":0,\"enabled\":true,\"threshold_percent\":25,\"duration_s\":30},{\"port\":1,\"enabled\":false,\"threshold_percent\":30,\"duration_s\":20}]},\"by_schedule\":{\"enabled\":false,\"entries\":[]}},\"light\":{\"by_schedule\":{\"enabled\":false,\"entries\":[]}}}";

  mqtt.InjectMessage(topic, payload);

  Core::Event event{};
  TEST_ASSERT_TRUE(queue.Pop(event));
  sync.OnEvent(ctx, event);

  Core::Event updated{};
  TEST_ASSERT_TRUE(queue.Pop(updated));
  TEST_ASSERT_EQUAL_INT(static_cast<int>(Core::EventType::kConfigUpdated), static_cast<int>(updated.type));

  char stored[512];
  TEST_ASSERT_TRUE(storage.ReadFile("/cfg/scenarios.json", stored, sizeof(stored)));

  Util::ScenariosConfig decoded{};
  TEST_ASSERT_TRUE(Util::DecodeScenariosConfig(stored, &decoded));
  TEST_ASSERT_TRUE(Util::ValidateScenariosConfig(decoded));
  TEST_ASSERT_TRUE(decoded.water_moisture.enabled);
  TEST_ASSERT_EQUAL_UINT32(3600, decoded.water_moisture.min_time_between_watering_s);
  TEST_ASSERT_TRUE(decoded.water_moisture.sensors[0].enabled);
  TEST_ASSERT_EQUAL_UINT8(25, decoded.water_moisture.sensors[0].threshold_percent);
  TEST_ASSERT_EQUAL_UINT32(30, decoded.water_moisture.sensors[0].duration_s);
}
