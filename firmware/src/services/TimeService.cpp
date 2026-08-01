/*
 * Chto v faile: realizaciya servisa setevogo UTC i vremennyh metok.
 * Rol v arhitekture: services.
 * Naznachenie: NTP-sinhronizaciya dlya TLS i metok telemetrii.
 * Soderzhit: retry, periodicheskii resync i validaciyu sistemnogo UTC.
 */

#include "services/TimeService.h"

#include <cstdio>
#include <ctime>

#include "services/time/ESP32NtpClientAdapter.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <WiFi.h>
#endif

namespace Services {

namespace {
const uint32_t kNtpStartupAttempts = 3;
const uint32_t kNtpRetryIntervalMs = 30000;
const uint32_t kNtpResyncIntervalMs = 21600000;
const uint32_t kNtpSyncTimeoutMs = 5000;
const int kMinValidYear = 2025;
const int kMaxValidYear = 2040;
const size_t kLogBufSize = 192;
}

void TimeService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init TimeService");
  Util::Logger::SetTimeProvider(this);

  cached_utc_ = 0;
  time_valid_ = false;
  next_retry_ms_ = 0;
  next_resync_ms_ = 0;
  retry_pending_ = false;
  resync_pending_ = false;
  sync_attempt_counter_ = 0;

  if (!ntp_) {
#if defined(ARDUINO)
    owned_ntp_.reset(new ESP32NtpClientAdapter());
    ntp_ = owned_ntp_.get();
#endif
  }
  if (!ntp_) {
    Util::Logger::Info("NTP klient ne nastroen, sinhronizaciya otklyuchena.");
    return;
  }

  ntp_->Begin();
  const uint32_t now_ms = GetNowMs();
  if (!IsWifiConnected()) {
    Util::Logger::Info("NTP: wifi ne podklyuchen, planiruem retry.");
    ScheduleRetry(now_ms, "wifi_offline");
    return;
  }

  for (uint32_t attempt = 0; attempt < kNtpStartupAttempts; ++attempt) {
    if (AttemptNtpSync("startup", now_ms)) {
      ScheduleResync(now_ms, "startup_ok");
      return;
    }
  }
  ScheduleRetry(now_ms, "startup_fail");
}

void TimeService::Loop(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  if (retry_pending_ && static_cast<int32_t>(now_ms - next_retry_ms_) >= 0) {
    retry_pending_ = false;
    if (AttemptNtpSync("retry", now_ms)) {
      ScheduleResync(now_ms, "retry_ok");
    } else {
      ScheduleRetry(now_ms, "retry_fail");
    }
  }
  if (resync_pending_ && static_cast<int32_t>(now_ms - next_resync_ms_) >= 0) {
    resync_pending_ = false;
    if (AttemptNtpSync("resync", now_ms)) {
      ScheduleResync(now_ms, "resync_ok");
    } else {
      ScheduleRetry(now_ms, "resync_fail");
    }
  }
}

bool TimeService::GetTime(TimeFields* out) const {
  if (!out) {
    return false;
  }
#if defined(UNIT_TEST)
  if (!test_synced_) {
    return false;
  }
  *out = test_fields_;
  return true;
#else
  std::time_t now = 0;
  if (!TryGetUtc(now)) {
    return false;
  }
  std::tm tm_info{};
#if defined(_WIN32)
  gmtime_s(&tm_info, &now);
#else
  gmtime_r(&now, &tm_info);
#endif
  out->year = static_cast<uint16_t>(tm_info.tm_year + 1900);
  out->month = static_cast<uint8_t>(tm_info.tm_mon + 1);
  out->day = static_cast<uint8_t>(tm_info.tm_mday);
  out->hour = static_cast<uint8_t>(tm_info.tm_hour);
  out->minute = static_cast<uint8_t>(tm_info.tm_min);
  out->second = static_cast<uint8_t>(tm_info.tm_sec);
  out->wday = static_cast<uint8_t>(tm_info.tm_wday);
  return true;
#endif
}

uint64_t TimeService::GetUnixTimeMs() const {
  uint64_t value = 0;
  return TryGetUnixTimeMs(&value) ? value : 0;
}

bool TimeService::TryGetUnixTimeMs(uint64_t* out_ms) const {
  if (!out_ms) {
    return false;
  }
#if defined(UNIT_TEST)
  if (!test_synced_) {
    return false;
  }
  *out_ms = test_unix_ms_;
  return true;
#else
  std::time_t now = 0;
  if (!TryGetUtc(now)) {
    return false;
  }
  *out_ms = static_cast<uint64_t>(now) * 1000ULL;
  return true;
#endif
}

bool TimeService::IsSynced() const {
  return HasValidTime();
}

bool TimeService::HasValidTime() const {
#if defined(UNIT_TEST)
  return test_synced_;
#else
  std::time_t now = 0;
  return TryGetUtc(now);
#endif
}

void TimeService::RequestSyncNow(uint32_t now_ms) {
  if (!ntp_ || HasValidTime()) {
    return;
  }
  retry_pending_ = true;
  resync_pending_ = false;
  next_retry_ms_ = now_ms;
}

bool TimeService::GetLogTimestamp(char* out, size_t out_size) const {
  if (!out || out_size == 0) {
    return false;
  }
  TimeFields fields{};
  if (!GetTime(&fields)) {
    return false;
  }
  const int written = std::snprintf(out,
                                    out_size,
                                    "%02u.%02u %02u:%02u:%02u",
                                    static_cast<unsigned int>(fields.day),
                                    static_cast<unsigned int>(fields.month),
                                    static_cast<unsigned int>(fields.hour),
                                    static_cast<unsigned int>(fields.minute),
                                    static_cast<unsigned int>(fields.second));
  return written > 0 && static_cast<size_t>(written) < out_size;
}

#if defined(UNIT_TEST)
void TimeService::SetTimeForTests(const TimeFields& fields, uint64_t unix_ms) {
  test_fields_ = fields;
  test_unix_ms_ = unix_ms;
  test_synced_ = true;
}

void TimeService::SetSyncedForTests(bool synced) {
  test_synced_ = synced;
}

void TimeService::SetNtpClientForTests(INtpClient* client) {
  ntp_ = client;
  owned_ntp_.reset();
}

void TimeService::SetNowMsForTests(uint32_t now_ms) {
  test_now_ms_ = now_ms;
}

bool TimeService::IsRetryPendingForTests() const {
  return retry_pending_;
}

bool TimeService::IsResyncPendingForTests() const {
  return resync_pending_;
}

uint32_t TimeService::GetNextRetryMsForTests() const {
  return next_retry_ms_;
}

uint32_t TimeService::GetNextResyncMsForTests() const {
  return next_resync_ms_;
}
#endif

bool TimeService::AttemptNtpSync(const char* context, uint32_t now_ms) {
  if (!ntp_ || !IsWifiConnected()) {
    return false;
  }

  ++sync_attempt_counter_;
  char log_buf[kLogBufSize];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: attempt #%lu (%s)",
                static_cast<unsigned long>(sync_attempt_counter_),
                context ? context : "unknown");
  Util::Logger::Info(log_buf);

  if (!ntp_->SyncOnce(kNtpSyncTimeoutMs)) {
    Util::Logger::Info("NTP: sync error or timeout");
    return false;
  }
  std::time_t ntp_utc = 0;
  if (!FetchNtpTime(ntp_utc) || !IsYearValid(ntp_utc)) {
    Util::Logger::Info("NTP: received time is invalid");
    return false;
  }

  const long long delta_sec = time_valid_
      ? static_cast<long long>(ntp_utc) - static_cast<long long>(cached_utc_)
      : 0;
  cached_utc_ = ntp_utc;
  time_valid_ = true;
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: sync success (%s), delta=%llds",
                context ? context : "unknown",
                delta_sec);
  Util::Logger::Info(log_buf);
  (void)now_ms;
  return true;
}

bool TimeService::FetchNtpTime(std::time_t& out_utc) const {
  return ntp_ && ntp_->GetTime(out_utc) && out_utc > 0;
}

bool TimeService::IsYearValid(std::time_t value) const {
  if (value <= 0) {
    return false;
  }
  std::tm tm_info{};
#if defined(_WIN32)
  gmtime_s(&tm_info, &value);
#else
  gmtime_r(&value, &tm_info);
#endif
  const int year = tm_info.tm_year + 1900;
  return year >= kMinValidYear && year <= kMaxValidYear;
}

bool TimeService::IsWifiConnected() const {
#if defined(ARDUINO)
  return WiFi.status() == WL_CONNECTED;
#else
  return true;
#endif
}

uint32_t TimeService::GetNowMs() const {
#if defined(UNIT_TEST)
  return test_now_ms_;
#elif defined(ARDUINO)
  return millis();
#else
  return 0;
#endif
}

bool TimeService::TryGetUtc(std::time_t& out_utc) const {
#if defined(ARDUINO)
  const std::time_t system_utc = std::time(nullptr);
  if (!IsYearValid(system_utc)) {
    return false;
  }
  out_utc = system_utc;
  return true;
#else
  if (!time_valid_ || !IsYearValid(cached_utc_)) {
    return false;
  }
  out_utc = cached_utc_;
  return true;
#endif
}

void TimeService::ScheduleRetry(uint32_t now_ms, const char* reason) {
  next_retry_ms_ = now_ms + kNtpRetryIntervalMs;
  retry_pending_ = true;
  resync_pending_ = false;
  char log_buf[kLogBufSize];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: retry cherez %lus (%s)",
                static_cast<unsigned long>(kNtpRetryIntervalMs / 1000U),
                reason ? reason : "unknown");
  Util::Logger::Info(log_buf);
}

void TimeService::ScheduleResync(uint32_t now_ms, const char* reason) {
  next_resync_ms_ = now_ms + kNtpResyncIntervalMs;
  resync_pending_ = true;
  retry_pending_ = false;
  char log_buf[kLogBufSize];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: resync cherez %lus (%s)",
                static_cast<unsigned long>(kNtpResyncIntervalMs / 1000U),
                reason ? reason : "unknown");
  Util::Logger::Info(log_buf);
}

} // namespace Services
