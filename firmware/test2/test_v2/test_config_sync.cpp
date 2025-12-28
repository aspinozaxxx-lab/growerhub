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
  std::remove("test_storage_sync/cfg/scenarios.json");
  std::remove("test_storage_sync/cfg");
  std::remove("test_storage_sync");

  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Services::StorageService storage;
  Modules::ConfigSyncModule sync;
  const Config::HardwareProfile& hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, &storage, nullptr, &sync, nullptr, &hw};
  storage.SetRootForTests("test_storage_sync");
  storage.Init(ctx);
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  sync.Init(ctx);

  char topic[128];
  Services::Topics::BuildCfgTopic(topic, sizeof(topic), hw.device_id);

  const char* payload =
      "{\"schema_version\":1,\"scenarios\":{\"water_time\":{\"enabled\":true},\"water_moisture\":{\"enabled\":false},\"light_schedule\":{\"enabled\":true}}}";

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
  TEST_ASSERT_TRUE(decoded.water_time_enabled);
  TEST_ASSERT_TRUE(decoded.light_schedule_enabled);
  TEST_ASSERT_FALSE(decoded.water_moisture_enabled);
}
