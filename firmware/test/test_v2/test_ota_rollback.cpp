#include <cstring>
#include <unity.h>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"
#include "modules/ActuatorModule.h"
#include "modules/OtaModule.h"
#include "services/MqttService.h"
#include "services/StorageService.h"
#include "services/ota/OtaInstaller.h"
#include "services/ota/OtaRollback.h"

static bool g_rollback_called = false;

static void RollbackHook() {
  g_rollback_called = true;
}

void test_ota_pending_confirm() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_ota1");
  Core::Context ctx{};
  storage.Init(ctx);

  Services::OtaRollback rollback;
  rollback.Init(&storage);
  rollback.SetRollbackHandler(&RollbackHook);

  rollback.MarkPending(1000);
  rollback.OnBoot(1000);
  TEST_ASSERT_TRUE(rollback.IsPending());

  rollback.ConfirmBoot();
  TEST_ASSERT_FALSE(rollback.IsPending());
}

void test_ota_boot_failures_trigger() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_ota2");
  Core::Context ctx{};
  storage.Init(ctx);

  Services::OtaRollback rollback;
  rollback.Init(&storage);
  rollback.SetRollbackHandler(&RollbackHook);

  g_rollback_called = false;
  rollback.MarkPending(0);
  rollback.OnBoot(0);
  rollback.OnBoot(0);
  rollback.OnBoot(0);

  TEST_ASSERT_TRUE(rollback.IsRollbackRequested());
  TEST_ASSERT_TRUE(g_rollback_called);
}

void test_ota_timeout_trigger() {
  Services::StorageService storage;
  storage.SetRootForTests("test/tmp/test_storage_ota3");
  Core::Context ctx{};
  storage.Init(ctx);

  Services::OtaRollback rollback;
  rollback.Init(&storage);
  rollback.SetRollbackHandler(&RollbackHook);

  g_rollback_called = false;
  rollback.MarkPending(1000);
  rollback.OnBoot(1000);
  rollback.OnBoot(401500);

  TEST_ASSERT_TRUE(rollback.IsRollbackRequested());
  TEST_ASSERT_TRUE(g_rollback_called);
}

namespace {

struct OtaPublishCapture {
  char payloads[3][320];
  int count;
};

OtaPublishCapture g_ota_capture{};

void CaptureOtaPublish(const char* topic, const char* payload, bool retain, int qos) {
  (void)topic;
  (void)retain;
  (void)qos;
  if (g_ota_capture.count >= 3) {
    return;
  }
  std::strncpy(
      g_ota_capture.payloads[g_ota_capture.count],
      payload ? payload : "",
      sizeof(g_ota_capture.payloads[g_ota_capture.count]) - 1);
  ++g_ota_capture.count;
}

class TestOtaInstaller : public Services::OtaInstaller {
 public:
  Services::OtaInstallResult Install(const char* url, const char* sha256) override {
    ++calls;
    std::strncpy(last_url, url ? url : "", sizeof(last_url) - 1);
    std::strncpy(last_sha, sha256 ? sha256 : "", sizeof(last_sha) - 1);
    return result;
  }

  Services::OtaInstallResult result = Services::OtaInstallResult::kOk;
  int calls = 0;
  char last_url[192]{};
  char last_sha[65]{};
};

class TestOtaRebooter : public Modules::OtaModule::Rebooter {
 public:
  void Restart() override { ++calls; }
  int calls = 0;
};

struct OtaFixture {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Services::MqttService mqtt;
  Services::StorageService storage;
  Modules::ActuatorModule actuator;
  Modules::OtaModule ota;
  const Config::HardwareProfile& hardware = Config::GetHardwareProfile();
  Core::Context context{
      &scheduler,
      &queue,
      &mqtt,
      &storage,
      nullptr,
      &actuator,
      nullptr,
      nullptr,
      &hardware,
      "GROVIKA_040AB1",
      &ota};

  void Init(const char* root) {
    std::memset(&g_ota_capture, 0, sizeof(g_ota_capture));
    storage.SetRootForTests(root);
    storage.Init(context);
    mqtt.Init(context);
    mqtt.SetConnectedForTests(true);
    mqtt.SetPublishHook(CaptureOtaPublish);
    actuator.Init(context);
    ota.Init(context);
  }
};

static const char* kOtaUrl = "https://growerhub.ru/firmware/grovika-1.esp32dev.bin";
static const char* kOtaSha =
    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

}

void test_ota_install_success_ack_and_reboot() {
  OtaFixture fixture;
  fixture.Init("test/tmp/test_ota_module_success");
  TestOtaInstaller installer;
  TestOtaRebooter rebooter;
  fixture.ota.SetInstaller(&installer);
  fixture.ota.SetRebooter(&rebooter);

  TEST_ASSERT_TRUE(fixture.ota.StartUpdate(kOtaUrl, "grovika-1", kOtaSha, "ota-ok"));
  TEST_ASSERT_EQUAL_INT(1, installer.calls);
  TEST_ASSERT_FALSE(fixture.mqtt.IsConnected());
  TEST_ASSERT_EQUAL_INT(0, rebooter.calls);
  TEST_ASSERT_EQUAL_INT(1, g_ota_capture.count);
  TEST_ASSERT_NOT_NULL(std::strstr(g_ota_capture.payloads[0], "\"status\":\"downloading\""));

  fixture.mqtt.SetConnectedForTests(true);
  fixture.ota.OnTick(fixture.context, 100);

  TEST_ASSERT_EQUAL_INT(1, rebooter.calls);
  TEST_ASSERT_EQUAL_INT(2, g_ota_capture.count);
  TEST_ASSERT_NOT_NULL(std::strstr(g_ota_capture.payloads[1], "\"status\":\"restarting\""));
}

void test_ota_install_success_reboots_after_ack_timeout() {
  OtaFixture fixture;
  fixture.Init("test/tmp/test_ota_module_timeout");
  TestOtaInstaller installer;
  TestOtaRebooter rebooter;
  fixture.ota.SetInstaller(&installer);
  fixture.ota.SetRebooter(&rebooter);

  TEST_ASSERT_TRUE(fixture.ota.StartUpdate(kOtaUrl, "grovika-1", kOtaSha, "ota-timeout"));
  TEST_ASSERT_FALSE(fixture.mqtt.IsConnected());
  fixture.ota.OnTick(fixture.context, 14999);
  TEST_ASSERT_EQUAL_INT(0, rebooter.calls);

  fixture.ota.OnTick(fixture.context, 15000);

  TEST_ASSERT_EQUAL_INT(1, rebooter.calls);
  TEST_ASSERT_EQUAL_INT(1, g_ota_capture.count);
}

void test_ota_install_error_ack() {
  OtaFixture fixture;
  fixture.Init("test/tmp/test_ota_module_error");
  TestOtaInstaller installer;
  TestOtaRebooter rebooter;
  installer.result = Services::OtaInstallResult::kSha256Mismatch;
  fixture.ota.SetInstaller(&installer);
  fixture.ota.SetRebooter(&rebooter);

  TEST_ASSERT_FALSE(fixture.ota.StartUpdate(kOtaUrl, "grovika-1", kOtaSha, "ota-error"));
  TEST_ASSERT_FALSE(fixture.mqtt.IsConnected());
  TEST_ASSERT_EQUAL_INT(0, rebooter.calls);
  TEST_ASSERT_EQUAL_INT(1, g_ota_capture.count);

  fixture.mqtt.SetConnectedForTests(true);
  fixture.ota.OnTick(fixture.context, 100);

  TEST_ASSERT_EQUAL_INT(2, g_ota_capture.count);
  TEST_ASSERT_NOT_NULL(std::strstr(g_ota_capture.payloads[1], "firmware_sha256_mismatch"));
}

void test_ota_declined_while_pump_runs() {
  OtaFixture fixture;
  fixture.Init("test/tmp/test_ota_module_pump");
  TestOtaInstaller installer;
  fixture.ota.SetInstaller(&installer);
  fixture.actuator.StartPump(5, "watering");

  TEST_ASSERT_FALSE(fixture.ota.StartUpdate(kOtaUrl, "grovika-1", kOtaSha, "ota-pump"));
  TEST_ASSERT_EQUAL_INT(0, installer.calls);
  TEST_ASSERT_EQUAL_INT(1, g_ota_capture.count);
  TEST_ASSERT_NOT_NULL(std::strstr(g_ota_capture.payloads[0], "pump_running"));
}

void test_ota_installer_rejects_untrusted_url() {
  Services::PlatformOtaInstaller installer;
  TEST_ASSERT_EQUAL_INT(
      static_cast<int>(Services::OtaInstallResult::kInvalidRequest),
      static_cast<int>(installer.Install("https://example.com/firmware.bin", kOtaSha)));
  TEST_ASSERT_EQUAL_INT(
      static_cast<int>(Services::OtaInstallResult::kInvalidRequest),
      static_cast<int>(installer.Install(
          "https://growerhub.ru/firmware/grovika-1.esp32c3_supermini.bin",
          kOtaSha)));
}
