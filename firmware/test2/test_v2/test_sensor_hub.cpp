#include <cstring>
#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"
#include "modules/ActuatorModule.h"
#include "modules/SensorHubModule.h"
#include "modules/StateModule.h"
#include "services/MqttService.h"
#include "services/TimeService.h"

// Bufers dlya feykovyh ADC vyborok pochvy.
static uint16_t g_samples_hub[2][9];
// Indeksy vyborok dlya feykovogo ADC.
static size_t g_index_hub[2];
// Buffer payload state dlya proverok.
static char g_state_payload[512];
// ID ustroystva dlya testov.
static const char* kDeviceId = "grovika_040AB1";

// Zapolnyaet bufer vyborok dlya 2 portov.
static void FillSamplesHub(uint16_t port0, uint16_t port1) {
  for (size_t i = 0; i < 9; ++i) {
    g_samples_hub[0][i] = port0;
    g_samples_hub[1][i] = port1;
  }
  g_index_hub[0] = 0;
  g_index_hub[1] = 0;
}

// Feykovyi ADC dlya pochvennyh datchikov.
static uint16_t FakeAdcHub(uint8_t pin) {
  size_t port = pin == 35 ? 1 : 0;
  uint16_t value = g_samples_hub[port][g_index_hub[port] % 9];
  g_index_hub[port]++;
  return value;
}

// Hook dlya perехvata MQTT publish.
static void PublishHook(const char* topic, const char* payload, bool retain, int qos) {
  (void)topic;
  (void)retain;
  (void)qos;
  if (!payload) {
    g_state_payload[0] = '\0';
    return;
  }
  std::strncpy(g_state_payload, payload, sizeof(g_state_payload) - 1);
  g_state_payload[sizeof(g_state_payload) - 1] = '\0';
}

// Zapusk odnogo tikа skanera s obnovleniem datchikov.
static void RunTick(Core::Scheduler& scheduler,
                    Modules::SensorHubModule& hub,
                    Core::Context& ctx,
                    uint32_t now_ms) {
  scheduler.Tick(ctx, now_ms);
  hub.OnTick(ctx, now_ms);
}

// Proverka blokirovki skanirovaniya pri rabote nasosa.
void test_sensor_hub_pump_block() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Modules::SensorHubModule hub;
  Config::HardwareProfile hw = Config::GetHardwareProfile();
  hw.has_dht22 = false;
  hw.dht_auto_reboot_on_fail = false;

  Core::Context ctx{&scheduler, &queue, nullptr, nullptr, nullptr, nullptr, nullptr, &hub, nullptr, &hw, kDeviceId};
  hub.Init(ctx);
  Drivers::Rj9PortScanner* scanner = hub.GetScanner();
  scanner->SetAdcReader(&FakeAdcHub);

  FillSamplesHub(2000, 4095);
  RunTick(scheduler, hub, ctx, 5000);
  RunTick(scheduler, hub, ctx, 10000);
  RunTick(scheduler, hub, ctx, 15000);
  TEST_ASSERT_TRUE(scanner->IsDetected(0));

  Core::Event pump_start{};
  pump_start.type = Core::EventType::kPumpStarted;
  pump_start.value = 16000;
  hub.OnEvent(ctx, pump_start);

  FillSamplesHub(4095, 4095);
  RunTick(scheduler, hub, ctx, 20000);
  RunTick(scheduler, hub, ctx, 25000);
  RunTick(scheduler, hub, ctx, 30000);
  TEST_ASSERT_TRUE(scanner->IsDetected(0));

  Core::Event pump_stop{};
  pump_stop.type = Core::EventType::kPumpStopped;
  pump_stop.value = 31000;
  hub.OnEvent(ctx, pump_stop);

  RunTick(scheduler, hub, ctx, 35000);
  RunTick(scheduler, hub, ctx, 40000);
  RunTick(scheduler, hub, ctx, 45000);
  TEST_ASSERT_FALSE(scanner->IsDetected(0));
}

// Proverka state payload dlya soil i DHT.
void test_state_soil_serialization() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::StateModule state;
  Config::HardwareProfile hw = Config::GetHardwareProfile();
  hw.has_dht22 = true;

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, &time_service, &actuator, nullptr, &hub, &state, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHook);

  Services::TimeFields fields{2025, 1, 1, 0, 0, 0, 3};
  time_service.SetTimeForTests(fields, 1735689600000ULL);

  actuator.Init(ctx);
  hub.Init(ctx);
  state.Init(ctx);

  Drivers::Rj9PortScanner* scanner = hub.GetScanner();
  scanner->SetAdcReader(&FakeAdcHub);
  Drivers::Dht22Sensor* dht = hub.GetDhtSensor();
  if (dht) {
    dht->SetReadHook([](float* out_temp_c, float* out_humidity) {
      if (!out_temp_c || !out_humidity) {
        return false;
      }
      *out_temp_c = 24.5f;
      *out_humidity = 60.1f;
      return true;
    });
  }
  FillSamplesHub(2000, 4095);
  RunTick(scheduler, hub, ctx, 5000);
  RunTick(scheduler, hub, ctx, 10000);
  RunTick(scheduler, hub, ctx, 15000);

  g_state_payload[0] = '\0';
  state.PublishState(true);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"soil\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"port\":0") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"port\":1") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"detected\":true") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"detected\":false") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"percent\":") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"port\":1,\"detected\":false,\"percent\"") == nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"light\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"status\":\"off\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"air\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"available\":true") != nullptr);
}

// Proverka pump status i started_at v state.
void test_state_pump_status_and_started_at() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::StateModule state;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, &time_service, &actuator, nullptr, nullptr, &state, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHook);

  Services::TimeFields fields{2025, 1, 1, 0, 0, 0, 3};
  time_service.SetTimeForTests(fields, 1735689600000ULL);

  actuator.Init(ctx);
  state.Init(ctx);

  TEST_ASSERT_TRUE(actuator.StartPump(30, "corr"));
  g_state_payload[0] = '\0';
  state.PublishState(true);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"pump\":{\"status\":\"on\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"started_at\":\"2025-01-01T00:00:00Z\"") != nullptr);

  actuator.StopPump("corr");
  g_state_payload[0] = '\0';
  state.PublishState(true);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"pump\":{\"status\":\"off\"") != nullptr);
}

// Proverka null started_at pri otsutstvii vremeni.
void test_state_started_at_null_without_time() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Services::TimeService time_service;
  Modules::ActuatorModule actuator;
  Modules::StateModule state;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, &time_service, &actuator, nullptr, nullptr, &state, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHook);

  time_service.SetSyncedForTests(false);

  actuator.Init(ctx);
  state.Init(ctx);

  TEST_ASSERT_TRUE(actuator.StartPump(30, "corr-null"));
  g_state_payload[0] = '\0';
  state.PublishState(true);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"started_at\":null") != nullptr);
}
