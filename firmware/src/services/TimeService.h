/*
 * Chto v faile: obyavleniya servisa setevogo UTC i vremennyh metok.
 * Rol v arhitekture: services.
 * Naznachenie: NTP-sinhronizaciya dlya TLS i metok telemetrii.
 * Soderzhit: publichnyi API, retry i periodicheskii resync.
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include <ctime>
#include <memory>

#include "core/Context.h"
#include "services/time/INtpClient.h"

namespace Services {

struct TimeFields {
  uint16_t year;
  uint8_t month;
  uint8_t day;
  uint8_t hour;
  uint8_t minute;
  uint8_t second;
  uint8_t wday;
};

class TimeService {
 public:
  void Init(Core::Context& ctx);
  void Loop(Core::Context& ctx, uint32_t now_ms);
  bool GetTime(TimeFields* out) const;
  uint64_t GetUnixTimeMs() const;
  bool TryGetUnixTimeMs(uint64_t* out_ms) const;
  bool IsSynced() const;
  bool HasValidTime() const;
  void RequestSyncNow(uint32_t now_ms);
  bool GetLogTimestamp(char* out, size_t out_size) const;

#if defined(UNIT_TEST)
  void SetTimeForTests(const TimeFields& fields, uint64_t unix_ms);
  void SetSyncedForTests(bool synced);
  void SetNtpClientForTests(INtpClient* client);
  void SetNowMsForTests(uint32_t now_ms);
  bool IsRetryPendingForTests() const;
  bool IsResyncPendingForTests() const;
  uint32_t GetNextRetryMsForTests() const;
  uint32_t GetNextResyncMsForTests() const;
#endif

 private:
  bool AttemptNtpSync(const char* context, uint32_t now_ms);
  bool FetchNtpTime(std::time_t& out_utc) const;
  bool IsYearValid(std::time_t value) const;
  bool IsWifiConnected() const;
  uint32_t GetNowMs() const;
  bool TryGetUtc(std::time_t& out_utc) const;
  void ScheduleRetry(uint32_t now_ms, const char* reason);
  void ScheduleResync(uint32_t now_ms, const char* reason);

  INtpClient* ntp_ = nullptr;
  std::unique_ptr<INtpClient> owned_ntp_;
  std::time_t cached_utc_ = 0;
  bool time_valid_ = false;
  uint32_t next_retry_ms_ = 0;
  uint32_t next_resync_ms_ = 0;
  bool retry_pending_ = false;
  bool resync_pending_ = false;
  uint32_t sync_attempt_counter_ = 0;
#if defined(UNIT_TEST)
  bool test_synced_ = false;
  TimeFields test_fields_{};
  uint64_t test_unix_ms_ = 0;
  uint32_t test_now_ms_ = 0;
#endif
};

} // namespace Services
