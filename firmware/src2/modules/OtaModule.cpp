#include "modules/OtaModule.h"

#include "core/Context.h"
#include "services/MqttService.h"
#include "util/Logger.h"

#if defined(ARDUINO)
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
  rollback_.Init(ctx.storage);
  rollback_.SetRollbackHandler(&RollbackHandler);
  boot_checked_ = false;
  boot_ms_ = 0;
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
    boot_ms_ = now_ms;
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
  if (now_ms - boot_ms_ >= kConfirmDelayMs) {
    rollback_.ConfirmBoot();
#if defined(ARDUINO)
    esp_ota_mark_app_valid_cancel_rollback();
#endif
  }
}

void OtaModule::MarkPending(uint32_t now_ms) {
  rollback_.MarkPending(now_ms);
}

}
