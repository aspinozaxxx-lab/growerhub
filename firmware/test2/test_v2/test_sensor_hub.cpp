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

static uint16_t g_samples_hub[2][9];
static size_t g_index_hub[2];
static char g_state_payload[512];

static void FillSamplesHub(uint16_t port0, uint16_t port1) {
  for (size_t i = 0; i < 9; ++i) {
    g_samples_hub[0][i] = port0;
    g_samples_hub[1][i] = port1;
  }
  g_index_hub[0] = 0;
  g_index_hub[1] = 0;
}

static uint16_t FakeAdcHub(uint8_t pin) {
  size_t port = pin == 35 ? 1 : 0;
  uint16_t value = g_samples_hub[port][g_index_hub[port] % 9];
  g_index_hub[port]++;
  return value;
}

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

static void RunTick(Core::Scheduler& scheduler,
                    Modules::SensorHubModule& hub,
                    Core::Context& ctx,
                    uint32_t now_ms) {
  scheduler.Tick(ctx, now_ms);
  hub.OnTick(ctx, now_ms);
}

void test_sensor_hub_pump_block() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Modules::SensorHubModule hub;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, nullptr, nullptr, nullptr, nullptr, &hub, nullptr, &hw};
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

void test_state_soil_serialization() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Modules::ActuatorModule actuator;
  Modules::SensorHubModule hub;
  Modules::StateModule state;
  Config::HardwareProfile hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, &actuator, nullptr, &hub, &state, &hw};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(&PublishHook);

  actuator.Init(ctx);
  hub.Init(ctx);
  state.Init(ctx);

  Drivers::Rj9PortScanner* scanner = hub.GetScanner();
  scanner->SetAdcReader(&FakeAdcHub);
  FillSamplesHub(2000, 4095);
  RunTick(scheduler, hub, ctx, 5000);
  RunTick(scheduler, hub, ctx, 10000);
  RunTick(scheduler, hub, ctx, 15000);

  g_state_payload[0] = '\0';
  state.PublishState(true);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"soil\"") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"port\":0") != nullptr);
  TEST_ASSERT_TRUE(std::strstr(g_state_payload, "\"detected\":true") != nullptr);
}
