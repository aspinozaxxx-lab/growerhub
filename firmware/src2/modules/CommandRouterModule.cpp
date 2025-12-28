/*
 * Chto v faile: realizaciya modulya marshrutizacii komand i reboot.
 * Rol v arhitekture: modules.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe modules.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "modules/CommandRouterModule.h"

#include <cstdio>
#include <cstring>

#if defined(ARDUINO)
#include <Arduino.h>
#endif

#include "core/Context.h"
#include "modules/ActuatorModule.h"
#include "modules/ConfigSyncModule.h"
#include "modules/StateModule.h"
#include "services/MqttService.h"
#include "services/Topics.h"
#include "util/Logger.h"
#include "util/MqttCodec.h"

namespace Modules {

static const char* CommandTypeToString(Util::CommandType type) {
  switch (type) {
    case Util::CommandType::kPumpStart:
      return "pump.start";
    case Util::CommandType::kPumpStop:
      return "pump.stop";
    case Util::CommandType::kReboot:
      return "reboot";
    case Util::CommandType::kCfgSync:
      return "cfg.sync";
    case Util::CommandType::kUnknown:
    default:
      return "unknown";
  }
}

static const uint32_t kRebootGraceDelayMs =
#if defined(UNIT_TEST)
    5;
#else
    250;
#endif

static void SleepMs(uint32_t delay_ms) {
#if defined(ARDUINO)
  delay(delay_ms);
#else
  (void)delay_ms;
#endif
}

void CommandRouterModule::Init(Core::Context& ctx) {
  mqtt_ = ctx.mqtt;
  actuator_ = ctx.actuator;
  config_sync_ = ctx.config_sync;
  state_ = ctx.state;
  device_id_ = ctx.device_id;
#if defined(ARDUINO)
  rebooter_ = &default_rebooter_;
#endif
  Util::Logger::Info("[CMD] init");
}

void CommandRouterModule::OnEvent(Core::Context& ctx, const Core::Event& event) {
  (void)ctx;
  if (event.type == Core::EventType::kMqttMessage) {
    HandleCommand(event.mqtt.topic, event.mqtt.payload);
    return;
  }
  if (event.type == Core::EventType::kRebootRequest) {
    RebootIfSafeInternal(nullptr, false, event.mqtt.payload);
  }
}

void CommandRouterModule::OnTick(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
}

void CommandRouterModule::SetRebooter(Rebooter* rebooter) {
  rebooter_ = rebooter;
}

void CommandRouterModule::HandleCommand(const char* topic, const char* payload) {
  if (!mqtt_ || !actuator_ || !topic || !payload) {
    return;
  }

  if (!Services::Topics::IsCmdTopic(topic, device_id_)) {
    return;
  }

  Util::Command command{};
  Util::ParseError error = Util::ParseError::kNone;
  if (!Util::ParseCommand(payload, command, error)) {
    char log_buf[192];
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "[CMD] parse error reason=%s",
                  Util::ParseErrorReason(error));
    Util::Logger::Info(log_buf);
    SendAckError(command.correlation_id, Util::ParseErrorReason(error));
    return;
  }
  char log_buf[256];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "[CMD] parsed type=%s duration_s=%u correlation_id=%s",
                CommandTypeToString(command.type),
                static_cast<unsigned int>(command.duration_s),
                command.correlation_id);
  Util::Logger::Info(log_buf);

  if (command.type == Util::CommandType::kPumpStart) {
    actuator_->StartPump(command.duration_s, command.correlation_id);
    if (command.correlation_id[0] == '\0') {
      SendAckError("", "bad command format: correlation_id missing");
    } else {
      SendAckStatus(command.correlation_id, "running", true);
    }
    return;
  }

  if (command.type == Util::CommandType::kPumpStop) {
    actuator_->StopPump(command.correlation_id);
    if (command.correlation_id[0] == '\0') {
      SendAckError("", "bad command format: correlation_id missing");
    } else {
      SendAckStatus(command.correlation_id, "idle", true);
    }
    return;
  }

  if (command.type == Util::CommandType::kReboot) {
    if (command.correlation_id[0] == '\0') {
      SendAckError("", "bad-correlation-id");
    } else {
      RebootIfSafe(command.correlation_id);
    }
    return;
  }

  if (command.type == Util::CommandType::kCfgSync) {
    if (config_sync_) {
      config_sync_->RequestSync();
    }
    return;
  }

  SendAckError(command.correlation_id, "unsupported command type");
}

void CommandRouterModule::SendAckStatus(const char* correlation_id, const char* status, bool accepted) {
  if (!mqtt_ || !mqtt_->IsConnected()) {
    return;
  }

  char topic[128];
  if (!Services::Topics::BuildAckTopic(topic, sizeof(topic), device_id_)) {
    return;
  }

  const char* result = accepted ? "accepted" : "declined";
  char payload[160];
  if (!Util::BuildAckStatus(correlation_id, result, status, payload, sizeof(payload))) {
    return;
  }

  mqtt_->Publish(topic, payload, false, 0);
}

void CommandRouterModule::SendAckError(const char* correlation_id, const char* reason) {
  if (!mqtt_ || !mqtt_->IsConnected()) {
    return;
  }

  char topic[128];
  if (!Services::Topics::BuildAckTopic(topic, sizeof(topic), device_id_)) {
    return;
  }

  char payload[192];
  if (!Util::BuildAckError(correlation_id, reason, payload, sizeof(payload))) {
    return;
  }

  mqtt_->Publish(topic, payload, false, 0);
}

void CommandRouterModule::RebootIfSafe(const char* correlation_id) {
  RebootIfSafeInternal(correlation_id, true, nullptr);
}

void CommandRouterModule::RebootIfSafeInternal(const char* correlation_id, bool send_ack, const char* reason) {
  const bool pump_running = actuator_ && actuator_->IsPumpRunning();
  const char* status = pump_running ? "running" : "idle";

  if (pump_running) {
    if (send_ack && correlation_id) {
      SendAckStatus(correlation_id, status, false);
    }
    return;
  }

  if (send_ack && correlation_id) {
    SendAckStatus(correlation_id, status, true);
  }
  if (reason && reason[0] != '\0') {
    Util::Logger::Info(reason);
  }
  if (state_) {
    state_->PublishState(false);
  }

  SleepMs(kRebootGraceDelayMs);
  if (rebooter_) {
    rebooter_->Restart();
  }
}

#if defined(ARDUINO)
void CommandRouterModule::EspRebooter::Restart() {
  ESP.restart();
}
#endif

}
