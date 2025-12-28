/*
 * Chto v faile: realizaciya servisa vremeni i vremennyh metok.
 * Rol v arhitekture: services.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe services.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "services/TimeService.h"

#include <ctime>

#include "util/Logger.h"

namespace Services {

void TimeService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init TimeService");
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
  const std::time_t now = std::time(nullptr);
  if (now < 1600000000) {
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
#if defined(UNIT_TEST)
  return test_synced_ ? test_unix_ms_ : 0;
#else
  const std::time_t now = std::time(nullptr);
  if (now < 1600000000) {
    return 0;
  }
  return static_cast<uint64_t>(now) * 1000ULL;
#endif
}

bool TimeService::IsSynced() const {
#if defined(UNIT_TEST)
  return test_synced_;
#else
  const std::time_t now = std::time(nullptr);
  return now >= 1600000000;
#endif
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
#endif

}
