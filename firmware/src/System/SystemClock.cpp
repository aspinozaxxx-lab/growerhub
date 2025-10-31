#include "SystemClock.h"

#include <cstdlib>
#include <memory>

#include "System/Time/IRTC.h"
#include "System/Time/INTPClient.h"
#include "System/Time/ILogger.h"
#include "System/Time/IScheduler.h"
#include "System/Time/DS3231RTCAdapter.h"

namespace {
    inline bool millisReached(unsigned long target) {
        return static_cast<long>(millis() - target) >= 0;
    }
}

SystemClock::SystemClock(IRTC* rtcSource, INTPClient* ntpClient, ILogger* loggerInstance, IScheduler* schedulerInstance)
    : rtc(rtcSource),
      ntp(ntpClient),
      logger(loggerInstance),
      scheduler(schedulerInstance),
      cachedUtc(0),
      timeValid(false),
      nextRetryMillis(0),
      nextResyncMillis(0),
      retryPending(false),
      resyncPending(false) {}

void SystemClock::begin() {
    if (!rtc) {
        if (!internalRtcAdapter) {
            internalRtcAdapter.reset(new DS3231RTCAdapter());
        }
        if (!internalRtcAdapter->begin()) {
            logWarn("RTC: устройство не отвечает, работаем без RTC.");
            rtc = nullptr;
        } else {
            logInfo("RTC: инициализация I2C (SDA=21, SCL=22).");
            rtc = internalRtcAdapter.get();
        }
    }
    retryPending = false;
    resyncPending = false;
    nextRetryMillis = 0;
    nextResyncMillis = 0;
    cachedUtc = 0;
    timeValid = false;

    if (rtc) {
        rtc->begin();
        updateFromRtc(); // Р—Р°РіСЂСѓР¶Р°РµРј РїСЂРµРґС‹РґСѓС‰РµРµ РІСЂРµРјСЏ, РµСЃР»Рё РѕРЅРѕ РІР°Р»РёРґРЅРѕ.
    }

    if (!ntp) {
        logWarn("NTP РєР»РёРµРЅС‚ РЅРµ Р·Р°РґР°РЅ, СЂР°Р±РѕС‚Р°РµРј С‚РѕР»СЊРєРѕ РїРѕ RTC.");
        return;
    }

    ntp->begin();

    bool synced = false;
    for (unsigned long attempt = 0; attempt < STARTUP_ATTEMPTS; ++attempt) {
        if (attemptNtpSync("startup")) {
            synced = true;
            break;
        }
    }

    if (synced) {
        scheduleResync(RESYNC_INTERVAL_MS);
    } else {
        logWarn("NTP: РЅР°С‡Р°Р»СЊРЅР°СЏ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ РЅРµ СѓРґР°Р»Р°СЃСЊ, РїР»Р°РЅРёСЂСѓРµРј СЂРµС‚СЂР°Рё.");
        scheduleRetry(RETRY_INTERVAL_MS);
    }
}

void SystemClock::loop() {
    if (scheduler) {
        return; // Р’РЅРµС€РЅРёР№ РїР»Р°РЅРёСЂРѕРІС‰РёРє РІС‹Р·РѕРІРµС‚ handleRetry/handleResync.
    }

    unsigned long now = millis();

    if (retryPending && millisReached(nextRetryMillis)) {
        retryPending = false;
        if (attemptNtpSync("loop retry")) {
            scheduleResync(RESYNC_INTERVAL_MS);
        } else {
            scheduleRetry(RETRY_INTERVAL_MS);
        }
    }

    if (resyncPending && millisReached(nextResyncMillis)) {
        resyncPending = false;
        if (attemptNtpSync("loop resync")) {
            scheduleResync(RESYNC_INTERVAL_MS);
        } else {
            scheduleRetry(RETRY_INTERVAL_MS);
        }
    }
}

bool SystemClock::isTimeSet() const {
    return timeValid && isYearValid(cachedUtc);
}

bool SystemClock::nowUtc(time_t& outUtc) const {
    if (timeValid && isYearValid(cachedUtc)) {
        outUtc = cachedUtc;
        return true;
    }

    time_t rtcTime = 0;
    if (getRtcUtc(rtcTime)) {
        cachedUtc = rtcTime;
        timeValid = true;
        outUtc = rtcTime;
        return true;
    }

    return false;
}

String SystemClock::formatIso8601(time_t value) const {
    if (value <= 0) {
        return String("1970-01-01T00:00:00Z");
    }

    struct tm timeInfo;
#if defined(__unix__) || defined(__APPLE__) || defined(ESP_PLATFORM)
    if (gmtime_r(&value, &timeInfo) == nullptr) {
        return String("1970-01-01T00:00:00Z");
    }
#else
    struct tm* tmp = gmtime(&value);
    if (!tmp) {
        return String("1970-01-01T00:00:00Z");
    }
    timeInfo = *tmp;
#endif

    char buffer[25];
    snprintf(buffer, sizeof(buffer), "%04d-%02d-%02dT%02d:%02d:%02dZ",
             timeInfo.tm_year + 1900,
             timeInfo.tm_mon + 1,
             timeInfo.tm_mday,
             timeInfo.tm_hour,
             timeInfo.tm_min,
             timeInfo.tm_sec);
    return String(buffer);
}

bool SystemClock::updateFromRtc() {
    time_t rtcTime = 0;
    if (!getRtcUtc(rtcTime)) {
        return false;
    }

    cachedUtc = rtcTime;
    timeValid = true;
    logInfo("RTC: загружено валидное время.");
    return true;
}

bool SystemClock::attemptNtpSync(const char* context) {
    if (!ntp) {
        return false;
    }

    if (!ntp->syncOnce()) {
        char warn[96];
        snprintf(warn, sizeof(warn), "NTP: РїРѕРїС‹С‚РєР° %s вЂ” РЅРµС‚ СЃРµС‚Рё/С‚Р°Р№РјР°СѓС‚.", context ? context : "unknown");
        logWarn(warn);
        return false;
    }

    time_t ntpUtc = 0;
    if (!fetchNtpTime(ntpUtc)) {
        char warn[96];
        snprintf(warn, sizeof(warn), "NTP: РїРѕРїС‹С‚РєР° %s вЂ” РєР»РёРµРЅС‚ РЅРµ РІРµСЂРЅСѓР» РІСЂРµРјСЏ.", context ? context : "unknown");
        logWarn(warn);
        return false;
    }

    if (!isYearValid(ntpUtc)) {
        char warn[96];
        snprintf(warn, sizeof(warn), "NTP: РїРѕРїС‹С‚РєР° %s вЂ” РіРѕРґ РІРЅРµ РґРёР°РїР°Р·РѕРЅР°.", context ? context : "unknown");
        logWarn(warn);
        return false;
    }

    time_t rtcUtc = 0;
    const bool rtcValid = getRtcUtc(rtcUtc);
    if (rtcValid) {
        const long long deltaRtc = std::llabs(static_cast<long long>(ntpUtc) - static_cast<long long>(rtcUtc));
        if (deltaRtc > MAX_ALLOWED_DRIFT_SECONDS) {
            char warn[128];
            snprintf(warn, sizeof(warn), "NTP: РїРѕРїС‹С‚РєР° %s вЂ” РїРѕРґРѕР·СЂРёС‚РµР»СЊРЅС‹Р№ СЃРґРІРёРі %llds, РѕС‚РєР»РѕРЅРµРЅРѕ.", context ? context : "unknown", deltaRtc);
            logWarn(warn);
            return false;
        }
    }

    time_t previousUtc = cachedUtc;
    const bool previousValid = timeValid && isYearValid(previousUtc);

    long long deltaRtc = 0;
    if (rtcValid) {
        deltaRtc = static_cast<long long>(ntpUtc) - static_cast<long long>(rtcUtc);
    } else if (previousValid) {
        deltaRtc = static_cast<long long>(ntpUtc) - static_cast<long long>(previousUtc);
    }

    if (rtc) {
        if (!rtc->setTime(ntpUtc)) {
            logWarn("RTC: не удалось записать время после NTP.");
        } else {
            char rtcMsg[96];
            snprintf(rtcMsg, sizeof(rtcMsg), "RTC: обновлено от NTP, delta=%llds.", deltaRtc);
            logInfo(rtcMsg);
        }
    }

    cachedUtc = ntpUtc;
    timeValid = true;

    long long deltaLog = deltaRtc;
    if (rtcValid) {
        deltaLog = static_cast<long long>(ntpUtc) - static_cast<long long>(rtcUtc);
    } else if (previousValid) {
        deltaLog = static_cast<long long>(ntpUtc) - static_cast<long long>(previousUtc);
    }

    char info[128];
    snprintf(info, sizeof(info), "NTP: СѓСЃРїРµС€РЅР°СЏ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ (%s), delta=%llds.", context ? context : "unknown", deltaLog);
    logInfo(info);
    return true;
}

bool SystemClock::fetchNtpTime(time_t& outUtc) {
    if (!ntp) {
        return false;
    }
    return ntp->getTime(outUtc) && outUtc > 0;
}

bool SystemClock::getRtcUtc(time_t& outUtc) const {
    if (!rtc) {
        return false;
    }
    if (!rtc->isTimeValid()) {
        return false;
    }
    if (!rtc->getTime(outUtc)) {
        return false;
    }
    return isYearValid(outUtc);
}

bool SystemClock::isYearValid(time_t value) const {
    if (value <= 0) {
        return false;
    }

    struct tm timeInfo;
#if defined(__unix__) || defined(__APPLE__) || defined(ESP_PLATFORM)
    if (gmtime_r(&value, &timeInfo) == nullptr) {
        return false;
    }
#else
    struct tm* tmp = gmtime(&value);
    if (!tmp) {
        return false;
    }
    timeInfo = *tmp;
#endif

    const int year = timeInfo.tm_year + 1900;
    return year >= MIN_VALID_YEAR && year <= MAX_VALID_YEAR;
}

void SystemClock::scheduleRetry(unsigned long delayMs) {
    if (!ntp) {
        return;
    }

    if (scheduler) {
        scheduler->scheduleDelayed("SystemClockRetry", delayMs, [this]() { this->handleRetry(); });
        return;
    }

    nextRetryMillis = millis() + delayMs;
    retryPending = true;
}

void SystemClock::scheduleResync(unsigned long delayMs) {
    if (!ntp) {
        return;
    }

    if (scheduler) {
        scheduler->scheduleDelayed("SystemClockResync", delayMs, [this]() { this->handleResync(); });
        return;
    }

    nextResyncMillis = millis() + delayMs;
    resyncPending = true;
}

void SystemClock::handleRetry() {
    if (attemptNtpSync("scheduled retry")) {
        scheduleResync(RESYNC_INTERVAL_MS);
    } else {
        scheduleRetry(RETRY_INTERVAL_MS);
    }
}

void SystemClock::handleResync() {
    if (attemptNtpSync("scheduled resync")) {
        scheduleResync(RESYNC_INTERVAL_MS);
    } else {
        scheduleRetry(RETRY_INTERVAL_MS);
    }
}

void SystemClock::logInfo(const char* message) const {
    if (logger) {
        logger->logInfo(TAG, message);
    }
}

void SystemClock::logWarn(const char* message) const {
    if (logger) {
        logger->logWarn(TAG, message);
    }
}

void SystemClock::logError(const char* message) const {
    if (logger) {
        logger->logError(TAG, message);
    }
}

