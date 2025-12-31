/*
 * Chto v faile: realizaciya modulya upravleniya nasosom i svetom.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/ActuatorModule.h"

#include <cstdio>
#include <cstring>

#include "config/HardwareProfile.h"
#include "core/Context.h"
#include "core/EventQueue.h"
#include "services/TimeService.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#endif

namespace Modules {

void ActuatorModule::Init(Core::Context& ctx) {
  const Config::HardwareProfile& profile = ctx.hardware ? *ctx.hardware : Config::GetHardwareProfile();

  max_runtime_ms_ = profile.pump_max_runtime_ms;
  pump_relay_.Init(profile.pins.pump_relay_pin, profile.pins.pump_relay_inverted);
  light_relay_.Init(profile.pins.light_relay_pin, profile.pins.light_relay_inverted);
  event_queue_ = ctx.event_queue;
  time_ = ctx.time;

  ResetManualState();
  Util::Logger::Info("init ActuatorModule");
}

void ActuatorModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  (void)event;
}

void ActuatorModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;

  if (!manual_active_) {
    return;
  }

  if (manual_start_ms_ == 0) {
    manual_start_ms_ = now_ms;
  }

  const uint64_t elapsed = static_cast<uint64_t>(now_ms - manual_start_ms_);
  const uint64_t requested_ms = static_cast<uint64_t>(manual_duration_s_) * 1000ULL;
  uint64_t limit_ms = requested_ms;
  if (max_runtime_ms_ > 0 && (limit_ms == 0 || static_cast<uint64_t>(max_runtime_ms_) < limit_ms)) {
    limit_ms = max_runtime_ms_;
  }

  if (limit_ms > 0 && elapsed >= limit_ms) {
    StopPumpInternal();
  }
}

bool ActuatorModule::StartPump(uint32_t duration_s, const char* correlation_id) {
  if (manual_active_ || duration_s == 0) {
    return false;
  }

  pump_relay_.Set(true);
  manual_active_ = true;
  manual_duration_s_ = duration_s;
  manual_start_ms_ = 0;

  if (correlation_id) {
    std::strncpy(manual_correlation_id_, correlation_id, sizeof(manual_correlation_id_) - 1);
    manual_correlation_id_[sizeof(manual_correlation_id_) - 1] = '\0';
  } else {
    manual_correlation_id_[0] = '\0';
  }

  manual_started_at_[0] = '\0';
  if (time_) {
    Services::TimeFields fields{};
    if (time_->GetTime(&fields)) {
      std::snprintf(manual_started_at_,
                    sizeof(manual_started_at_),
                    "%04u-%02u-%02uT%02u:%02u:%02uZ",
                    static_cast<unsigned int>(fields.year),
                    static_cast<unsigned int>(fields.month),
                    static_cast<unsigned int>(fields.day),
                    static_cast<unsigned int>(fields.hour),
                    static_cast<unsigned int>(fields.minute),
                    static_cast<unsigned int>(fields.second));
    }
  }

  if (event_queue_) {
    Core::Event event{};
    event.type = Core::EventType::kPumpStarted;
    event.value = GetEventTimestampMs();
    event_queue_->Push(event);
  }

  return true;
}

void ActuatorModule::StopPump(const char* correlation_id) {
  (void)correlation_id;
  StopPumpInternal();
}

bool ActuatorModule::IsPumpRunning() const {
  return manual_active_ && pump_relay_.Get();
}

bool ActuatorModule::IsLightOn() const {
  return light_relay_.Get();
}

void ActuatorModule::SetLight(bool on) {
  light_relay_.Set(on);
}

ManualWateringState ActuatorModule::GetManualWateringState() const {
  ManualWateringState state{};
  state.active = manual_active_;
  state.duration_s = manual_duration_s_;
  state.started_at = manual_started_at_;
  state.correlation_id = manual_correlation_id_;
  return state;
}

void ActuatorModule::ResetManualState() {
  manual_active_ = false;
  manual_duration_s_ = 0;
  manual_start_ms_ = 0;
  manual_correlation_id_[0] = '\0';
  manual_started_at_[0] = '\0';
  pump_relay_.Set(false);
}

void ActuatorModule::StopPumpInternal() {
  const bool was_running = manual_active_ && pump_relay_.Get();

  pump_relay_.Set(false);
  manual_active_ = false;
  manual_duration_s_ = 0;
  manual_start_ms_ = 0;
  manual_correlation_id_[0] = '\0';
  manual_started_at_[0] = '\0';

  if (was_running && event_queue_) {
    Core::Event event{};
    event.type = Core::EventType::kPumpStopped;
    event.value = GetEventTimestampMs();
    event_queue_->Push(event);
  }
}

uint64_t ActuatorModule::GetEventTimestampMs() const {
  if (!time_) {
    return 0;
  }
  uint64_t unix_ms = 0;
  if (!time_->TryGetUnixTimeMs(&unix_ms)) {
    return 0;
  }
  return unix_ms;
}

uint32_t ActuatorModule::GetNowMs() {
#if defined(ARDUINO)
  return millis();
#else
  static uint32_t fake_now = 0;
  fake_now += 1;
  return fake_now;
#endif
}

}
