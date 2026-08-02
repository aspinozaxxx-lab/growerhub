/*
 * Chto v faile: realizaciya modulya podtverzhdeniya OTA i rollback.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/OtaModule.h"

#include <cstring>

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

namespace {
static const uint32_t kRestartAckTimeoutMs = 15000;
}

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
  ClearPendingResult();
  Util::Logger::Info("init OtaModule");
}

void OtaModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void OtaModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  if (pending_result_ == PendingResult::kFailure) {
    if (mqtt_ && mqtt_->IsConnected()
        && SendAck(
            pending_correlation_id_,
            "error",
            "failed",
            pending_version_,
            pending_reason_)) {
      ClearPendingResult();
    }
    return;
  }
  if (pending_result_ == PendingResult::kRestart) {
    if (mqtt_ && mqtt_->IsConnected()
        && SendAck(
            pending_correlation_id_,
            "accepted",
            "restarting",
            pending_version_,
            nullptr)) {
      RestartUpdatedFirmware();
      return;
    }
    if (static_cast<int32_t>(now_ms - pending_since_ms_)
        >= static_cast<int32_t>(kRestartAckTimeoutMs)) {
      Util::Logger::Info("[OTA] restarting without MQTT ack after timeout");
      RestartUpdatedFirmware();
    }
    return;
  }
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
  if (pending_result_ != PendingResult::kNone) {
    SendAck(correlation_id, "declined", "failed", version, "ota_busy");
    return false;
  }
  if (actuator_ && actuator_->IsPumpRunning()) {
    SendAck(correlation_id, "declined", "failed", version, "pump_running");
    return false;
  }
  if (!mqtt_ || !mqtt_->IsConnected()) {
    return false;
  }

  if (!SendAck(correlation_id, "accepted", "downloading", version, nullptr)) {
    return false;
  }
  mqtt_->DisconnectForOta();
  const Services::OtaInstallResult result = installer_->Install(url, sha256);
  uint32_t now_ms = 0;
#if defined(ARDUINO)
  now_ms = millis();
#endif
  if (result != Services::OtaInstallResult::kOk) {
    StorePendingResult(
        PendingResult::kFailure,
        correlation_id,
        version,
        Services::OtaInstallResultReason(result),
        now_ms);
    return false;
  }

  rollback_.MarkPending(now_ms);
  StorePendingResult(PendingResult::kRestart, correlation_id, version, nullptr, now_ms);
  return true;
}

void OtaModule::SetInstaller(Services::OtaInstaller* installer) {
  installer_ = installer;
}

void OtaModule::SetRebooter(Rebooter* rebooter) {
  rebooter_ = rebooter;
}

bool OtaModule::SendAck(const char* correlation_id, const char* result, const char* status,
                        const char* version, const char* reason) {
  if (!mqtt_ || !mqtt_->IsConnected()) {
    return false;
  }
  char topic[128];
  if (!Services::Topics::BuildAckTopic(topic, sizeof(topic), device_id_)) {
    return false;
  }
  char payload[320];
  if (!Util::BuildOtaAck(
          correlation_id, result, status, version, reason, payload, sizeof(payload))) {
    return false;
  }
  return mqtt_->Publish(topic, payload, false, 0);
}

void OtaModule::StorePendingResult(PendingResult result, const char* correlation_id,
                                   const char* version, const char* reason, uint32_t now_ms) {
  pending_result_ = result;
  pending_since_ms_ = now_ms;
  std::strncpy(pending_correlation_id_,
               correlation_id ? correlation_id : "",
               sizeof(pending_correlation_id_) - 1);
  pending_correlation_id_[sizeof(pending_correlation_id_) - 1] = '\0';
  std::strncpy(pending_version_, version ? version : "", sizeof(pending_version_) - 1);
  pending_version_[sizeof(pending_version_) - 1] = '\0';
  std::strncpy(pending_reason_, reason ? reason : "", sizeof(pending_reason_) - 1);
  pending_reason_[sizeof(pending_reason_) - 1] = '\0';
}

void OtaModule::ClearPendingResult() {
  pending_result_ = PendingResult::kNone;
  pending_since_ms_ = 0;
  pending_correlation_id_[0] = '\0';
  pending_version_[0] = '\0';
  pending_reason_[0] = '\0';
}

void OtaModule::RestartUpdatedFirmware() {
  if (!rebooter_) {
    return;
  }
  ClearPendingResult();
  Util::Logger::Info("[OTA] firmware verified, restarting");
#if defined(ARDUINO)
  delay(250);
#endif
  rebooter_->Restart();
}

#if defined(ARDUINO)
void OtaModule::EspRebooter::Restart() {
  ESP.restart();
}
#endif

}
