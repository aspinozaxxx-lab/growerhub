/*
 * Chto v faile: realizaciya adapttera NTP dlya ESP32.
 * Rol v arhitekture: services/time.
 * Naznachenie: vyzov configTime i ozhidanie gettimeofday.
 * Soderzhit: sinhronizaciyu s tajmautom i kesh vremeni.
 */

#include "services/time/ESP32NtpClientAdapter.h"

#if defined(ARDUINO)
#include <Arduino.h>
#if defined(ESP_PLATFORM)
#include <sys/time.h>
#include <time.h>
#endif
#endif

namespace {
  // Server NTP po umolchaniyu.
  const char* kDefaultServer = "pool.ntp.org";
  // Interval oprosa gettimeofday v millisekundah.
  const uint32_t kPollIntervalMs = 100;
}

namespace Services {

ESP32NtpClientAdapter::ESP32NtpClientAdapter(const char* server)
    : server_(ResolveServer(server)),
      last_sync_utc_(0),
      has_fresh_(false),
      in_progress_(false) {}

bool ESP32NtpClientAdapter::Begin() {
  last_sync_utc_ = 0;
  has_fresh_ = false;
  in_progress_ = false;
  return true;
}

bool ESP32NtpClientAdapter::SyncOnce(uint32_t timeout_ms) {
  in_progress_ = true;
  has_fresh_ = false;
  last_sync_utc_ = 0;

#if defined(ARDUINO)
  configTime(0, 0, server_);
  const unsigned long start = millis();
  timeval tv{};
  while ((millis() - start) <= timeout_ms) {
    if (gettimeofday(&tv, nullptr) == 0 && tv.tv_sec > 0) {
      last_sync_utc_ = static_cast<std::time_t>(tv.tv_sec);
      has_fresh_ = true;
      in_progress_ = false;
      return true;
    }
    delay(kPollIntervalMs);
  }
#else
  (void)timeout_ms;
#endif

  in_progress_ = false;
  return false;
}

bool ESP32NtpClientAdapter::GetTime(std::time_t& out_utc) const {
  if (!has_fresh_) {
    return false;
  }
  out_utc = last_sync_utc_;
  return true;
}

bool ESP32NtpClientAdapter::IsSyncInProgress() const {
  return in_progress_;
}

const char* ESP32NtpClientAdapter::ResolveServer(const char* candidate) const {
  if (candidate && candidate[0] != '\0') {
    return candidate;
  }
  return kDefaultServer;
}

} // namespace Services


