#include <cstring>
#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "drivers/soil/Rj9PortScanner.h"
#include "modules/ActuatorModule.h"
#include "modules/AutomationModule.h"
#include "modules/ConfigSyncModule.h"
#include "modules/SensorHubModule.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/TimeService.h"
#include "util/JsonUtil.h"

static uint16_t g_samples_auto[2][9];
static size_t g_index_auto[2];
static int g_publish_count = 0;
static int g_last_qos = -1;
static char g_last_payload[256];
static const char* kDeviceId = "grovika_040AB1";

static void FillSamplesAuto(uint16_t port0, uint16_t port1) {
  for (size_t i = 0; i < 9; ++i) {
    g_samples_auto[0][i] = port0;
    g_samples_auto[1][i] = port1;
  }
  g_index_auto[0] = 0;
  g_index_auto[1] = 0;
}

static uint16_t FakeAdcAuto(uint8_t pin) {
  size_t port = pin == 35 ? 1 : 0;
  uint16_t value = g_samples_auto[port][g_index_auto[port] % 9];
  g_index_auto[port]++;
  return value;
}

static void PublishHookAuto(const char* topic, const char* payload, bool retain, int qos) {
  (void)topic;
  (void)retain;
  g_publish_count++;
  g_last_qos = qos;
  if (!payload) {
    g_last_payload[0] = '\0';
    return;
  }
  std::strncpy(g_last_payload, payload, sizeof(g_last_payload) - 1);
  g_last_payload[sizeof(g_last_payload) - 1] = '\0';
}

static void WriteConfigToStorage(Services::StorageService& storage, const Util::ScenariosConfig& cfg) {
  char json[1024];
  TEST_ASSERT_TRUE(Util::EncodeScenariosConfig(cfg, json, sizeof(json)));
  TEST_ASSERT_TRUE(storage.WriteFileAtomic("/cfg/scenarios.json", json));
}

static void PrimeScanner(Modules::SensorHubModule& hub) {
  Drivers::Rj9PortScanner* scanner = hub.GetScanner();
  scanner->SetAdcReader(&FakeAdcAuto);
  scanner->Scan();
  scanner->Scan();
  scanner->Scan();
}

void test_auto_moisture_port0() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_auto");
  Core::Context ctx{};
  storage.Init(ctx);

  Util::ScenariosConfig cfg = Util::DefaultScenariosConfig();
  cfg.water_moisture.enabled = true;
  cfg.water_moisture.sensors[0].enabled = true;
  cfg.water_moisture.sensors[0].threshold_percent = 25;
  cfg.water_moisture.sensors[0].duration_s = 20;
  WriteConfigToStorage(storage, cfg);

  Core::EventQueue queue;
  Modules::ConfigSyncModule sync;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::AutomationModule automation;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context full{nullptr,
                     &queue,
                     &mqtt,
                     &storage,
                     &time_service,
                     &actuator,
                     &sync,
                     &hub,
                     nullptr,
                     &hw,
                     kDeviceId};
  mqtt.Init(full);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHookAuto);
  sync.Init(full);
  actuator.Init(full);
  hub.Init(full);
  automation.Init(full);

  Services::TimeFields fields{2025, 1, 1, 0, 0, 0, 3};
  time_service.SetTimeForTests(fields, 1735689600000ULL);

  FillSamplesAuto(3800, 4095);
  PrimeScanner(hub);

  g_publish_count = 0;
  g_last_qos = -1;
  g_last_payload[0] = '\0';
  automation.OnTick(full, 5000);

  TEST_ASSERT_TRUE(actuator.IsPumpRunning());
  TEST_ASSERT_EQUAL_INT(1, g_publish_count);
  TEST_ASSERT_EQUAL_INT(1, g_last_qos);
  TEST_ASSERT_TRUE(std::strstr(g_last_payload, "\"mode\":\"moisture\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_last_payload, "\"event_id\":\"") != nullptr);
}

void test_auto_moisture_port1() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_auto2");
  Core::Context ctx{};
  storage.Init(ctx);

  Util::ScenariosConfig cfg = Util::DefaultScenariosConfig();
  cfg.water_moisture.enabled = true;
  cfg.water_moisture.sensors[1].enabled = true;
  cfg.water_moisture.sensors[1].threshold_percent = 25;
  cfg.water_moisture.sensors[1].duration_s = 15;
  WriteConfigToStorage(storage, cfg);

  Core::EventQueue queue;
  Modules::ConfigSyncModule sync;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::AutomationModule automation;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context full{nullptr,
                     &queue,
                     &mqtt,
                     &storage,
                     &time_service,
                     &actuator,
                     &sync,
                     &hub,
                     nullptr,
                     &hw,
                     kDeviceId};
  mqtt.Init(full);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHookAuto);
  sync.Init(full);
  actuator.Init(full);
  hub.Init(full);
  automation.Init(full);

  Services::TimeFields fields{2025, 1, 1, 0, 0, 0, 3};
  time_service.SetTimeForTests(fields, 1735689600000ULL);

  FillSamplesAuto(4095, 3800);
  PrimeScanner(hub);

  g_publish_count = 0;
  g_last_qos = -1;
  automation.OnTick(full, 6000);

  TEST_ASSERT_TRUE(actuator.IsPumpRunning());
  TEST_ASSERT_EQUAL_INT(1, g_publish_count);
  TEST_ASSERT_EQUAL_INT(1, g_last_qos);
}

void test_auto_min_time_between() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_auto3");
  Core::Context ctx{};
  storage.Init(ctx);

  Util::ScenariosConfig cfg = Util::DefaultScenariosConfig();
  cfg.water_moisture.enabled = true;
  cfg.water_moisture.min_time_between_watering_s = 60;
  cfg.water_moisture.sensors[0].enabled = true;
  cfg.water_moisture.sensors[0].threshold_percent = 25;
  cfg.water_moisture.sensors[0].duration_s = 10;
  WriteConfigToStorage(storage, cfg);

  Core::EventQueue queue;
  Modules::ConfigSyncModule sync;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::AutomationModule automation;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context full{nullptr,
                     &queue,
                     &mqtt,
                     &storage,
                     &time_service,
                     &actuator,
                     &sync,
                     &hub,
                     nullptr,
                     &hw,
                     kDeviceId};
  mqtt.Init(full);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHookAuto);
  sync.Init(full);
  actuator.Init(full);
  hub.Init(full);
  automation.Init(full);

  Services::TimeFields fields{2025, 1, 1, 0, 0, 0, 3};
  time_service.SetTimeForTests(fields, 1735689600000ULL);

  FillSamplesAuto(3800, 4095);
  PrimeScanner(hub);

  g_publish_count = 0;
  automation.OnTick(full, 0);
  actuator.StopPump("test");

  automation.OnTick(full, 1000);
  TEST_ASSERT_EQUAL_INT(1, g_publish_count);
}

void test_auto_schedule_trigger_once() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_auto4");
  Core::Context ctx{};
  storage.Init(ctx);

  Util::ScenariosConfig cfg = Util::DefaultScenariosConfig();
  cfg.water_schedule.enabled = true;
  cfg.water_schedule.entry_count = 1;
  cfg.water_schedule.entries[0].start_hhmm = 730;
  cfg.water_schedule.entries[0].duration_s = 12;
  cfg.water_schedule.entries[0].days_mask = 1 << 2;
  WriteConfigToStorage(storage, cfg);

  Core::EventQueue queue;
  Modules::ConfigSyncModule sync;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::AutomationModule automation;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context full{nullptr,
                     &queue,
                     &mqtt,
                     &storage,
                     &time_service,
                     &actuator,
                     &sync,
                     &hub,
                     nullptr,
                     &hw,
                     kDeviceId};
  mqtt.Init(full);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHookAuto);
  sync.Init(full);
  actuator.Init(full);
  hub.Init(full);
  automation.Init(full);

  Services::TimeFields fields{2025, 1, 7, 7, 30, 0, 2};
  time_service.SetTimeForTests(fields, 1000000);

  g_publish_count = 0;
  automation.OnTick(full, 2000);
  automation.OnTick(full, 2500);
  TEST_ASSERT_EQUAL_INT(1, g_publish_count);
}

void test_light_schedule_overnight() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_auto5");
  Core::Context ctx{};
  storage.Init(ctx);

  Util::ScenariosConfig cfg = Util::DefaultScenariosConfig();
  cfg.light_schedule.enabled = true;
  cfg.light_schedule.entry_count = 1;
  cfg.light_schedule.entries[0].start_hhmm = 2200;
  cfg.light_schedule.entries[0].end_hhmm = 600;
  cfg.light_schedule.entries[0].days_mask = 1 << 1;
  WriteConfigToStorage(storage, cfg);

  Core::EventQueue queue;
  Modules::ConfigSyncModule sync;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::AutomationModule automation;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context full{nullptr,
                     &queue,
                     &mqtt,
                     &storage,
                     &time_service,
                     &actuator,
                     &sync,
                     &hub,
                     nullptr,
                     &hw,
                     kDeviceId};
  mqtt.Init(full);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHookAuto);
  sync.Init(full);
  actuator.Init(full);
  hub.Init(full);
  automation.Init(full);

  Services::TimeFields late{2025, 1, 6, 23, 0, 0, 1};
  time_service.SetTimeForTests(late, 2000000);
  automation.OnTick(full, 3000);
  TEST_ASSERT_TRUE(actuator.IsLightOn());

  Services::TimeFields early{2025, 1, 7, 1, 0, 0, 2};
  time_service.SetTimeForTests(early, 2005000);
  automation.OnTick(full, 4000);
  TEST_ASSERT_TRUE(actuator.IsLightOn());

  Services::TimeFields off{2025, 1, 7, 7, 0, 0, 2};
  time_service.SetTimeForTests(off, 2010000);
  automation.OnTick(full, 5000);
  TEST_ASSERT_FALSE(actuator.IsLightOn());
}
