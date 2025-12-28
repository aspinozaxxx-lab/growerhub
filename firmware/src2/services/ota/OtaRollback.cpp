/*
 * Chto v faile: realizaciya hraneniya sostoyaniya OTA i logiki rollback.
 * Rol v arhitekture: services.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe services.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "services/ota/OtaRollback.h"

#include <cctype>
#include <cstdio>
#include <cstring>

#include "services/StorageService.h"
#include "util/Logger.h"

namespace Services {

const char* OtaRollback::kStatePath = "/cfg/device.json";

static const char* SkipWs(const char* ptr) {
  const char* current = ptr;
  while (current && *current && std::isspace(static_cast<unsigned char>(*current))) {
    ++current;
  }
  return current;
}

static bool ExtractUintField(const char* json, const char* key, uint64_t& out) {
  if (!json || !key) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  if (!key_pos) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value || !std::isdigit(static_cast<unsigned char>(*value))) {
    return false;
  }
  uint64_t result = 0;
  while (*value && std::isdigit(static_cast<unsigned char>(*value))) {
    result = result * 10 + static_cast<uint64_t>(*value - '0');
    ++value;
  }
  out = result;
  return true;
}

static bool ExtractBoolField(const char* json, const char* key, bool& out) {
  if (!json || !key) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  if (!key_pos) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value) {
    return false;
  }
  if (std::strncmp(value, "true", 4) == 0) {
    out = true;
    return true;
  }
  if (std::strncmp(value, "false", 5) == 0) {
    out = false;
    return true;
  }
  return false;
}

void OtaRollback::Init(StorageService* storage) {
  storage_ = storage;
  rollback_requested_ = false;
  state_ = State{kSchemaVersion, false, 0, 0};
  LoadState();
}

void OtaRollback::SetRollbackHandler(RollbackHandler handler) {
  rollback_handler_ = handler;
}

void OtaRollback::MarkPending(uint64_t now_ms) {
  state_.schema_version = kSchemaVersion;
  state_.pending = true;
  state_.boot_fail_count = 0;
  state_.pending_since_ms = now_ms;
  SaveState();
}

void OtaRollback::OnBoot(uint64_t now_ms) {
  if (!state_.pending) {
    return;
  }
  if (state_.pending_since_ms == 0) {
    state_.pending_since_ms = now_ms;
  }
  if (state_.boot_fail_count < 255) {
    state_.boot_fail_count++;
  }
  SaveState();

  if (state_.boot_fail_count >= kMaxBootFailures) {
    RequestRollback();
    return;
  }
  if (now_ms > 0 && state_.pending_since_ms > 0 &&
      now_ms > state_.pending_since_ms &&
      now_ms - state_.pending_since_ms > kPendingTimeoutMs) {
    RequestRollback();
  }
}

void OtaRollback::ConfirmBoot() {
  if (!state_.pending) {
    return;
  }
  state_.pending = false;
  state_.boot_fail_count = 0;
  state_.pending_since_ms = 0;
  SaveState();
}

bool OtaRollback::IsPending() const {
  return state_.pending;
}

bool OtaRollback::IsRollbackRequested() const {
  return rollback_requested_;
}

#if defined(UNIT_TEST)
const OtaRollback::State& OtaRollback::GetState() const {
  return state_;
}
#endif

bool OtaRollback::LoadState() {
  if (!storage_) {
    return false;
  }
  char payload[256];
  if (!storage_->ReadFile(kStatePath, payload, sizeof(payload))) {
    return false;
  }

  uint64_t schema = 0;
  if (!ExtractUintField(payload, "schema_version", schema)) {
    return false;
  }

  State loaded = State{kSchemaVersion, false, 0, 0};
  loaded.schema_version = static_cast<uint32_t>(schema);
  bool pending = false;
  if (ExtractBoolField(payload, "pending", pending)) {
    loaded.pending = pending;
  }
  uint64_t fails = 0;
  if (ExtractUintField(payload, "boot_fail_count", fails)) {
    loaded.boot_fail_count = static_cast<uint8_t>(fails);
  }
  uint64_t since = 0;
  if (ExtractUintField(payload, "pending_since_ms", since)) {
    loaded.pending_since_ms = since;
  }

  if (loaded.schema_version != kSchemaVersion) {
    return false;
  }

  state_ = loaded;
  return true;
}

bool OtaRollback::SaveState() const {
  if (!storage_) {
    return false;
  }
  char payload[256];
  const int written = std::snprintf(
      payload,
      sizeof(payload),
      "{\"schema_version\":%u,\"pending\":%s,\"boot_fail_count\":%u,\"pending_since_ms\":%llu}",
      static_cast<unsigned int>(kSchemaVersion),
      state_.pending ? "true" : "false",
      static_cast<unsigned int>(state_.boot_fail_count),
      static_cast<unsigned long long>(state_.pending_since_ms));
  if (written <= 0 || static_cast<size_t>(written) >= sizeof(payload)) {
    return false;
  }
  return storage_->WriteFileAtomic(kStatePath, payload);
}

void OtaRollback::RequestRollback() {
  rollback_requested_ = true;
  if (rollback_handler_) {
    rollback_handler_();
  } else {
    Util::Logger::Info("ota rollback requested");
  }
}

}
