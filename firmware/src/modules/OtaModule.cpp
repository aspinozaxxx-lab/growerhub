/*
 * Chto v faile: realizaciya modulya podtverzhdeniya OTA i rollback.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/OtaModule.h"

#include "core/Context.h"
#include "modules/ActuatorModule.h"
#include "services/MqttService.h"
#include "services/Topics.h"
#include "util/Logger.h"
#include "util/MqttCodec.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <esp_ota_ops.h>
#endif

namespace Modules {

static void RollbackHandler() {
#if defined(ARDUINO)
  esp_ota_mark_app_invalid_rollback_and_reboot();
#else
  Util::Logger::Info("ota rollback requested");
#endif
}

void OtaModule::Init(Core::Context& ctx) {
  mqtt_ = ctx.mqtt;
  actuator_ = ctx.actuator;
  device_id_ = ctx.device_id;
  installer_ = &default_installer_;
#if defined(ARDUINO)
  rebooter_ = &default_rebooter_;
#endif
  rollback_.Init(ctx.storage);
  rollback_.SetRollbackHandler(&RollbackHandler);
  boot_checked_ = false;
  Util::Logger::Info("init OtaModule");
}

void OtaModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void OtaModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  if (!boot_checked_) {
    rollback_.OnBoot(now_ms);
    boot_checked_ = true;
  }
  if (!rollback_.IsPending() || rollback_.IsRollbackRequested()) {
    return;
  }
  if (mqtt_ && mqtt_->IsConnected()) {
    rollback_.ConfirmBoot();
#if defined(ARDUINO)
    esp_ota_mark_app_valid_cancel_rollback();
#endif
    return;
  }
}

void OtaModule::MarkPending(uint32_t now_ms) {
  rollback_.MarkPending(now_ms);
}

bool OtaModule::StartUpdate(const char* url, const char* version, const char* sha256,
                            const char* correlation_id) {
  if (!installer_ || !version || !correlation_id || correlation_id[0] == '\0') {
    return false;
  }
  if (actuator_ && actuator_->IsPumpRunning()) {
    SendAck(correlation_id, "declined", "failed", version, "pump_running");
    return false;
  }
  if (!mqtt_ || !mqtt_->IsConnected()) {
    return false;
  }

  SendAck(correlation_id, "accepted", "downloading", version, nullptr);
  const Services::OtaInstallResult result = installer_->Install(url, sha256);
  if (result != Services::OtaInstallResult::kOk) {
    SendAck(
        correlation_id,
        "error",
        "failed",
        version,
        Services::OtaInstallResultReason(result));
    return false;
  }

  uint32_t now_ms = 0;
#if defined(ARDUINO)
  now_ms = millis();
#endif
  rollback_.MarkPending(now_ms);
  SendAck(correlation_id, "accepted", "restarting", version, nullptr);
  Util::Logger::Info("[OTA] firmware verified, restarting");
#if defined(ARDUINO)
  delay(250);
#endif
  if (rebooter_) {
    rebooter_->Restart();
  }
  return true;
}

void OtaModule::SetInstaller(Services::OtaInstaller* installer) {
  installer_ = installer;
}

void OtaModule::SetRebooter(Rebooter* rebooter) {
  rebooter_ = rebooter;
}

void OtaModule::SendAck(const char* correlation_id, const char* result, const char* status,
                        const char* version, const char* reason) {
  if (!mqtt_ || !mqtt_->IsConnected()) {
    return;
  }
  char topic[128];
  if (!Services::Topics::BuildAckTopic(topic, sizeof(topic), device_id_)) {
    return;
  }
  char payload[320];
  if (!Util::BuildOtaAck(
          correlation_id, result, status, version, reason, payload, sizeof(payload))) {
    return;
  }
  mqtt_->Publish(topic, payload, false, 0);
}

#if defined(ARDUINO)
void OtaModule::EspRebooter::Restart() {
  ESP.restart();
}
#endif

}
