/*
 * Chto v faile: realizaciya servisa vremeni i vremennyh metok.
 * Rol v arhitekture: services.
 * Naznachenie: logika vremeni i sinhronizacii po NTP.
 * Soderzhit: logiku retry/resync i validaciyu UTC.
 */

#include "services/TimeService.h"

#include <cstdio>
#include <cstdlib>
#include <ctime>

#include "services/time/ESP32NtpClientAdapter.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <WiFi.h>
#endif

namespace Services {

namespace {
  // Kolichestvo popytok NTP na starte.
  const uint32_t kNtpStartupAttempts = 3;
  // Interval retry posle oshibki (ms).
  const uint32_t kNtpRetryIntervalMs = 30000;
  // Interval planovogo resync (ms).
  const uint32_t kNtpResyncIntervalMs = 21600000;
  // Tajmaut odnoi NTP sinhronizacii (ms).
  const uint32_t kNtpSyncTimeoutMs = 5000;
  // Porog podozritelnogo sdviga dlya drift-check (sek).
  const uint32_t kSuspiciousDriftSec = 31U * 24U * 3600U;
  // Minimalno dopustimyi god UTC.
  const int kMinValidYear = 2025;
  // Maksimalno dopustimyi god UTC.
  const int kMaxValidYear = 2040;
  // Minimalnyi epoch RTC dlya bazovoi proverki.
  const std::time_t kRtcMinEpoch = 1735689600;
  // Buffer dlya log-strok s korotkimi soobshcheniyami.
  const size_t kLogBufSize = 192;
}

// Init servisa vremeni i NTP logiki.
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
  last_sync_ok_ = false;
  last_sync_delta_sec_ = 0;
  last_sync_ms_ = 0;

  if (rtc_) {
    std::time_t rtc_utc = 0;
    if (GetRtcUtc(rtc_utc)) {
      char log_buf[kLogBufSize];
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "RTC: start read ok, utc=%lld.",
                    static_cast<long long>(rtc_utc));
      Util::Logger::Info(log_buf);
    } else {
      Util::Logger::Info("RTC: start read fail ili nevalidno.");
    }
  } else {
    Util::Logger::Info("RTC: provider ne nastroen.");
  }

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

  bool synced = false;
  for (uint32_t attempt = 0; attempt < kNtpStartupAttempts; ++attempt) {
    if (AttemptNtpSync("startup", now_ms)) {
      synced = true;
      break;
    }
  }

  if (synced) {
    ScheduleResync(now_ms, "startup_ok");
  } else {
    Util::Logger::Info("NTP: nachalnyi sync ne udalsya, planiruem retry.");
    ScheduleRetry(now_ms, "startup_fail");
  }
}

// Periodicheskiy loop dlya retry/resync.
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

// Poluchenie tekushchego vremeni s validaciey.
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
  if (!TryGetBestUtc(now)) {
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

// Poluchenie unix millis s validaciey.
uint64_t TimeService::GetUnixTimeMs() const {
#if defined(UNIT_TEST)
  return test_synced_ ? test_unix_ms_ : 0;
#else
  uint64_t unix_ms = 0;
  if (!TryGetUnixTimeMs(&unix_ms)) {
    return 0;
  }
  return unix_ms;
#endif
}

// Poluchenie unix ms tolko pri validnom vremeni.
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
  if (!TryGetBestUtc(now)) {
    return false;
  }
  *out_ms = static_cast<uint64_t>(now) * 1000ULL;
  return true;
#endif
}

// Priznak validnogo vremeni.
bool TimeService::IsSynced() const {
#if defined(UNIT_TEST)
  return test_synced_;
#else
  return HasValidTime();
#endif
}

// Priznak nalichiya vremeni iz system ili RTC.
bool TimeService::HasValidTime() const {
#if defined(UNIT_TEST)
  return test_synced_;
#else
  std::time_t now = 0;
  return TryGetBestUtc(now);
#endif
}

// Formirovanie timestamp dlya loga.
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

// Ustanovka RTC providera dlya runtime.
void TimeService::SetRtcProvider(IRtcProvider* rtc) {
  rtc_ = rtc;
}

#if defined(UNIT_TEST)
// Zadanie vremeni dlya testov.
void TimeService::SetTimeForTests(const TimeFields& fields, uint64_t unix_ms) {
  test_fields_ = fields;
  test_unix_ms_ = unix_ms;
  test_synced_ = true;
}

// Ustanovka flaga sinhronizacii dlya testov.
void TimeService::SetSyncedForTests(bool synced) {
  test_synced_ = synced;
}

// Podmena NTP klienta dlya testov.
void TimeService::SetNtpClientForTests(INtpClient* client) {
  ntp_ = client;
  owned_ntp_.reset();
}

// Podmena RTC providera dlya testov.
void TimeService::SetRtcProviderForTests(IRtcProvider* rtc) {
  rtc_ = rtc;
}

// Podmena millis dlya testov.
void TimeService::SetNowMsForTests(uint32_t now_ms) {
  test_now_ms_ = now_ms;
}

// Priznak pending retry dlya testov.
bool TimeService::IsRetryPendingForTests() const {
  return retry_pending_;
}

// Priznak pending resync dlya testov.
bool TimeService::IsResyncPendingForTests() const {
  return resync_pending_;
}

// Planovoe vremya retry dlya testov.
uint32_t TimeService::GetNextRetryMsForTests() const {
  return next_retry_ms_;
}

// Planovoe vremya resync dlya testov.
uint32_t TimeService::GetNextResyncMsForTests() const {
  return next_resync_ms_;
}
#endif

// Popytka NTP sinhronizacii s validaciei i logami.
bool TimeService::AttemptNtpSync(const char* context, uint32_t now_ms) {
  if (!ntp_) {
    return false;
  }

  if (!IsWifiConnected()) {
    Util::Logger::Info("NTP: wifi ne podklyuchen, popytka propushchena.");
    last_sync_ok_ = false;
    last_sync_delta_sec_ = 0;
    last_sync_ms_ = now_ms;
    return false;
  }

  ++sync_attempt_counter_;
  char log_buf[kLogBufSize];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: popytka #%lu (%s)",
                static_cast<unsigned long>(sync_attempt_counter_),
                context ? context : "unknown");
  Util::Logger::Info(log_buf);

  if (!ntp_->SyncOnce(kNtpSyncTimeoutMs)) {
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "NTP: popytka %s - fail ili tajmaut.",
                  context ? context : "unknown");
    Util::Logger::Info(log_buf);
    last_sync_ok_ = false;
    last_sync_delta_sec_ = 0;
    last_sync_ms_ = now_ms;
    return false;
  }

  std::time_t ntp_utc = 0;
  if (!FetchNtpTime(ntp_utc)) {
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "NTP: popytka %s - vremya ne polucheno.",
                  context ? context : "unknown");
    Util::Logger::Info(log_buf);
    last_sync_ok_ = false;
    last_sync_delta_sec_ = 0;
    last_sync_ms_ = now_ms;
    return false;
  }

  if (!IsYearValid(ntp_utc)) {
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "NTP: popytka %s - god vne diapazona.",
                  context ? context : "unknown");
    Util::Logger::Info(log_buf);
    last_sync_ok_ = false;
    last_sync_delta_sec_ = 0;
    last_sync_ms_ = now_ms;
    return false;
  }

  std::time_t rtc_utc = 0;
  bool rtc_valid = false;
  if (rtc_) {
    rtc_valid = GetRtcUtc(rtc_utc);
    if (!rtc_valid) {
      Util::Logger::Info("RTC: net validnogo vremeni, drift-check propushchen.");
    }
  } else {
    Util::Logger::Info("RTC: net, drift-check propushchen.");
  }

  if (rtc_valid) {
    const long long delta_check =
        std::llabs(static_cast<long long>(ntp_utc) - static_cast<long long>(rtc_utc));
    if (delta_check > static_cast<long long>(kSuspiciousDriftSec)) {
      std::snprintf(log_buf,
                    sizeof(log_buf),
                    "NTP: popytka %s - podozritelnyi sdvig %llds, otkloneno.",
                    context ? context : "unknown",
                    delta_check);
      Util::Logger::Info(log_buf);
      last_sync_ok_ = false;
      last_sync_delta_sec_ = 0;
      last_sync_ms_ = now_ms;
      return false;
    }
  }

  const std::time_t previous_utc = cached_utc_;
  const bool previous_valid = time_valid_ && IsYearValid(previous_utc);

  cached_utc_ = ntp_utc;
  time_valid_ = true;

  long long delta_sec = 0;
  if (rtc_valid) {
    delta_sec = static_cast<long long>(ntp_utc) - static_cast<long long>(rtc_utc);
  } else if (previous_valid) {
    delta_sec = static_cast<long long>(ntp_utc) - static_cast<long long>(previous_utc);
  }

  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: uspeshnaya sinhronizaciya (%s), delta=%llds.",
                context ? context : "unknown",
                delta_sec);
  Util::Logger::Info(log_buf);

  last_sync_ok_ = true;
  last_sync_delta_sec_ = delta_sec;
  last_sync_ms_ = now_ms;
  return true;
}

// Poluchenie vremeni iz NTP klienta.
bool TimeService::FetchNtpTime(std::time_t& out_utc) const {
  if (!ntp_) {
    return false;
  }
  if (!ntp_->GetTime(out_utc)) {
    return false;
  }
  return out_utc > 0;
}

// Poluchenie vremeni iz RTC s validaciey.
bool TimeService::GetRtcUtc(std::time_t& out_utc) const {
  if (!rtc_) {
    return false;
  }
  if (!rtc_->GetUtc(out_utc)) {
    return false;
  }
  return IsYearValid(out_utc) && IsRtcEpochValid(out_utc);
}

// Proverka goda na dopustimyi diapazon.
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

// Proverka sostoyaniya Wi-Fi.
bool TimeService::IsWifiConnected() const {
#if defined(ARDUINO)
  return WiFi.status() == WL_CONNECTED;
#else
  return true;
#endif
}

// Poluchenie tekushchego millis s podmenoi v testah.
uint32_t TimeService::GetNowMs() const {
#if defined(UNIT_TEST)
  return test_now_ms_;
#elif defined(ARDUINO)
  return millis();
#else
  static uint32_t fake_now = 0;
  fake_now += 10;
  return fake_now;
#endif
}

// Vybor luchshego UTC vremena (system ili RTC).
bool TimeService::TryGetBestUtc(std::time_t& out_utc) const {
  const std::time_t system_utc = std::time(nullptr);
  if (IsYearValid(system_utc)) {
    out_utc = system_utc;
    return true;
  }
  std::time_t rtc_utc = 0;
  if (GetRtcUtc(rtc_utc)) {
    out_utc = rtc_utc;
    return true;
  }
  return false;
}

// Proverka minimalnogo RTC epoch poroga.
bool TimeService::IsRtcEpochValid(std::time_t value) const {
  return value >= kRtcMinEpoch;
}

// Planirovanie retry NTP.
void TimeService::ScheduleRetry(uint32_t now_ms, const char* reason) {
  next_retry_ms_ = now_ms + kNtpRetryIntervalMs;
  retry_pending_ = true;
  resync_pending_ = false;

  char log_buf[kLogBufSize];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: planiruem retry cherez %lus (%s).",
                static_cast<unsigned long>(kNtpRetryIntervalMs / 1000U),
                reason ? reason : "unknown");
  Util::Logger::Info(log_buf);
}

// Planirovanie resync NTP.
void TimeService::ScheduleResync(uint32_t now_ms, const char* reason) {
  next_resync_ms_ = now_ms + kNtpResyncIntervalMs;
  resync_pending_ = true;
  retry_pending_ = false;

  char log_buf[kLogBufSize];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "NTP: planiruem resync cherez %lus (%s).",
                static_cast<unsigned long>(kNtpResyncIntervalMs / 1000U),
                reason ? reason : "unknown");
  Util::Logger::Info(log_buf);
}

} // namespace Services


