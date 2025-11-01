#include "ESP32NTPClientAdapter.h"

#if defined(ESP_PLATFORM)
#include <Arduino.h>
#include <sys/time.h>
#include <time.h>
#else
#include <Arduino.h>
#endif

namespace {
    constexpr const char* DEFAULT_SERVER = "pool.ntp.org";
}

ESP32NTPClientAdapter::ESP32NTPClientAdapter(const char* server)
    : server_(resolveServer(server)),
      lastSyncUtc_(0),
      hasFresh_(false),
      inProgress_(false) {}

bool ESP32NTPClientAdapter::begin() {
    lastSyncUtc_ = 0;
    hasFresh_ = false;
    inProgress_ = false;
    return true;
}

bool ESP32NTPClientAdapter::syncOnce() {
    return syncOnce(5000);
}

bool ESP32NTPClientAdapter::syncOnce(uint32_t timeoutMs) {
    inProgress_ = true;
    hasFresh_ = false;
    lastSyncUtc_ = 0;

#if defined(ESP_PLATFORM)
    configTime(0, 0, server_);
    const unsigned long start = millis();
    timeval tv{};
    while ((millis() - start) <= timeoutMs) {
        if (gettimeofday(&tv, nullptr) == 0) {
            if (tv.tv_sec > 0) {
                lastSyncUtc_ = static_cast<time_t>(tv.tv_sec);
                hasFresh_ = true;
                inProgress_ = false;
                return true;
            }
        }
        delay(100);
    }
#else
    (void)timeoutMs;
#endif

    inProgress_ = false;
    return false;
}

bool ESP32NTPClientAdapter::getTime(time_t& outUtc) const {
    if (!hasFresh_) {
        return false;
    }
    outUtc = lastSyncUtc_;
    return true;
}

bool ESP32NTPClientAdapter::isSyncInProgress() const {
    return inProgress_;
}

const char* ESP32NTPClientAdapter::resolveServer(const char* candidate) const {
    if (candidate && candidate[0] != '\0') {
        return candidate;
    }
    return DEFAULT_SERVER;
}

