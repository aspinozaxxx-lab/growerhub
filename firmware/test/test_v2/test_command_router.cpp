#include <cstring>
#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"
#include "modules/ActuatorModule.h"
#include "modules/CommandRouterModule.h"
#include "services/MqttService.h"
#include "services/Topics.h"

struct PublishCapture {
  char topic[128];
  char payload[256];
  int count;
};

static PublishCapture g_capture;
static const char* kDeviceId = "grovika_040AB1";

static void ResetCapture() {
  std::memset(&g_capture, 0, sizeof(g_capture));
}

static void CapturePublish(const char* topic, const char* payload, bool retain, int qos) {
  (void)retain;
  (void)qos;
  std::strncpy(g_capture.topic, topic ? topic : "", sizeof(g_capture.topic) - 1);
  g_capture.topic[sizeof(g_capture.topic) - 1] = '\0';
  std::strncpy(g_capture.payload, payload ? payload : "", sizeof(g_capture.payload) - 1);
  g_capture.payload[sizeof(g_capture.payload) - 1] = '\0';
  ++g_capture.count;
}

class TestRebooter : public Modules::CommandRouterModule::Rebooter {
 public:
  void Restart() override { ++calls; }
  int calls = 0;
};

void test_pump_start_ack() {
  ResetCapture();
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Modules::ActuatorModule actuator;
  Modules::CommandRouterModule router;
  const Config::HardwareProfile& hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, nullptr, &actuator, nullptr, nullptr, nullptr, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(CapturePublish);
  actuator.Init(ctx);
  router.Init(ctx);

  char cmd_topic[128];
  Services::Topics::BuildCmdTopic(cmd_topic, sizeof(cmd_topic), kDeviceId);

  Core::Event event{};
  event.type = Core::EventType::kMqttMessage;
  std::strncpy(event.mqtt.topic, cmd_topic, sizeof(event.mqtt.topic) - 1);
  std::strncpy(event.mqtt.payload,
               R"({"type":"pump.start","duration_s":5,"correlation_id":"a1"})",
               sizeof(event.mqtt.payload) - 1);

  router.OnEvent(ctx, event);

  TEST_ASSERT_TRUE(actuator.IsPumpRunning());
  TEST_ASSERT_EQUAL_INT(1, g_capture.count);
  TEST_ASSERT_EQUAL_STRING(R"({"correlation_id":"a1","result":"accepted","status":"running"})",
                           g_capture.payload);
}

void test_pump_stop_ack() {
  ResetCapture();
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Modules::ActuatorModule actuator;
  Modules::CommandRouterModule router;
  const Config::HardwareProfile& hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, nullptr, &actuator, nullptr, nullptr, nullptr, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(CapturePublish);
  actuator.Init(ctx);
  router.Init(ctx);

  actuator.StartPump(5, "a2");

  char cmd_topic[128];
  Services::Topics::BuildCmdTopic(cmd_topic, sizeof(cmd_topic), kDeviceId);

  Core::Event event{};
  event.type = Core::EventType::kMqttMessage;
  std::strncpy(event.mqtt.topic, cmd_topic, sizeof(event.mqtt.topic) - 1);
  std::strncpy(event.mqtt.payload,
               R"({"type":"pump.stop","correlation_id":"a2"})",
               sizeof(event.mqtt.payload) - 1);

  router.OnEvent(ctx, event);

  TEST_ASSERT_FALSE(actuator.IsPumpRunning());
  TEST_ASSERT_EQUAL_INT(1, g_capture.count);
  TEST_ASSERT_EQUAL_STRING(R"({"correlation_id":"a2","result":"accepted","status":"idle"})",
                           g_capture.payload);
}

void test_reboot_declined_when_pump_running() {
  ResetCapture();
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Modules::ActuatorModule actuator;
  Modules::CommandRouterModule router;
  TestRebooter rebooter;
  const Config::HardwareProfile& hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, nullptr, &actuator, nullptr, nullptr, nullptr, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(CapturePublish);
  actuator.Init(ctx);
  router.Init(ctx);
  router.SetRebooter(&rebooter);

  actuator.StartPump(5, "r1");

  char cmd_topic[128];
  Services::Topics::BuildCmdTopic(cmd_topic, sizeof(cmd_topic), kDeviceId);

  Core::Event event{};
  event.type = Core::EventType::kMqttMessage;
  std::strncpy(event.mqtt.topic, cmd_topic, sizeof(event.mqtt.topic) - 1);
  std::strncpy(event.mqtt.payload,
               R"({"type":"reboot","correlation_id":"r1"})",
               sizeof(event.mqtt.payload) - 1);

  router.OnEvent(ctx, event);

  TEST_ASSERT_EQUAL_INT(0, rebooter.calls);
  TEST_ASSERT_EQUAL_INT(1, g_capture.count);
  TEST_ASSERT_EQUAL_STRING(R"({"correlation_id":"r1","result":"declined","status":"running"})",
                           g_capture.payload);
}

void test_reboot_accepted_when_idle() {
  ResetCapture();
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Modules::ActuatorModule actuator;
  Modules::CommandRouterModule router;
  TestRebooter rebooter;
  const Config::HardwareProfile& hw = Config::GetHardwareProfile();

  Core::Context ctx{&scheduler, &queue, &mqtt, nullptr, nullptr, &actuator, nullptr, nullptr, nullptr, &hw, kDeviceId};
  mqtt.Init(ctx);
  mqtt.SetConnectedForTests(true);
  mqtt.SetPublishHook(CapturePublish);
  actuator.Init(ctx);
  router.Init(ctx);
  router.SetRebooter(&rebooter);

  char cmd_topic[128];
  Services::Topics::BuildCmdTopic(cmd_topic, sizeof(cmd_topic), kDeviceId);

  Core::Event event{};
  event.type = Core::EventType::kMqttMessage;
  std::strncpy(event.mqtt.topic, cmd_topic, sizeof(event.mqtt.topic) - 1);
  std::strncpy(event.mqtt.payload,
               R"({"type":"reboot","correlation_id":"r2"})",
               sizeof(event.mqtt.payload) - 1);

  router.OnEvent(ctx, event);

  TEST_ASSERT_EQUAL_INT(1, rebooter.calls);
  TEST_ASSERT_EQUAL_INT(1, g_capture.count);
  TEST_ASSERT_EQUAL_STRING(R"({"correlation_id":"r2","result":"accepted","status":"idle"})",
                           g_capture.payload);
}
