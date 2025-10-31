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
      internalRtcAdapter(nullptr),
      cachedUtc(0),
      timeValid(false),
      nextRetryMillis(0),
      nextResyncMillis(0),
      retryPending(false),
      resyncPending(false),
      syncAttemptCounter(0),
      lastNtpSyncOk(false),
      lastNtpDelta(0),
      lastNtpMillis(0) {}

void SystemClock::begin() {
    debugLog("start begin");
    if (!rtc) {
        if (!internalRtcAdapter) {
            internalRtcAdapter.reset(new DS3231RTCAdapter());
        }
        if (!internalRtcAdapter->begin()) {
            debugLog("RTC init fail, fallback bez RTC");
            logWarn("RTC: ustroistvo ne otvechaet, rabotaem bez RTC.");
            rtc = nullptr;
        } else {
            debugLog("RTC init ok");
            logInfo("RTC: inicializacia I2C (SDA=21, SCL=22).");
            rtc = internalRtcAdapter.get();
        }
    }

    retryPending = false;
    resyncPending = false;
    nextRetryMillis = 0;
    nextResyncMillis = 0;
    cachedUtc = 0;
    timeValid = false;
    syncAttemptCounter = 0;

    bool rtcHadTime = false;
    if (rtc) {
        rtc->begin();
        rtcHadTime = updateFromRtc();
        if (rtcHadTime) {
            debugLog("RTC time valid posle starta");
        } else {
            debugLog("RTC time ne valid posle starta");
        }
    } else {
        debugLog("RTC nedostupen posle starta");
    }

    if (!ntp) {
        debugLog("NTP klient ne zadat");
        logWarn("NTP klient ne zadat, rabotaem tolko po RTC.");
        lastNtpSyncOk = false;
        lastNtpDelta = 0;
        lastNtpMillis = millis();
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
        scheduleResync(NTP_RESYNC_INTERVAL_MS);
    } else {
        debugLog("NTP startup ne udalsia, planiruem retry");
        logWarn("NTP: nachalnaia sinhronizacia ne udalas, planiruem retry.");
        scheduleRetry(NTP_RETRY_INTERVAL_MS);
    }
    debugLog("start end");
}

void SystemClock::loop() {
    if (scheduler) {
        return; // Vneshnii planirovshchik vyzovet handleRetry/handleResync.
    }

    unsigned long now = millis();

    if (retryPending && millisReached(nextRetryMillis)) {
        retryPending = false;
        if (attemptNtpSync("loop retry")) {
            scheduleResync(NTP_RESYNC_INTERVAL_MS);
        } else {
            scheduleRetry(NTP_RETRY_INTERVAL_MS);
        }
    }

    if (resyncPending && millisReached(nextResyncMillis)) {
        resyncPending = false;
        if (attemptNtpSync("loop resync")) {
            scheduleResync(NTP_RESYNC_INTERVAL_MS);
        } else {
            scheduleRetry(NTP_RETRY_INTERVAL_MS);
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

void SystemClock::dumpStatusToSerial() {
#if !defined(ARDUINO)
    (void)syncAttemptCounter;
    (void)lastNtpSyncOk;
    (void)lastNtpDelta;
    (void)lastNtpMillis;
    return;
#else
    Serial.println("[CLOCK] dump begin");
    Serial.print("[CLOCK] is_time_set=");
    Serial.println(isTimeSet() ? "yes" : "no");

    if (timeValid) {
        Serial.print("[CLOCK] cached=");
        Serial.println(formatIso8601(cachedUtc));
    } else {
        Serial.println("[CLOCK] cached=n/a");
    }

    if (rtc) {
        time_t rtcUtc = 0;
        if (rtc->isTimeValid() && rtc->getTime(rtcUtc)) {
            Serial.print("[CLOCK] rtc=");
            Serial.println(formatIso8601(rtcUtc));
        } else {
            Serial.println("[CLOCK] rtc=n/a");
        }
    } else {
        Serial.println("[CLOCK] rtc=absent");
    }

    const unsigned long nowMs = scheduler ? scheduler->nowMs() : millis();

    Serial.print("[CLOCK] retry_pending=");
    Serial.print(retryPending ? "yes" : "no");
    if (retryPending) {
        Serial.print(" left=");
        Serial.println(static_cast<long long>(nextRetryMillis) - static_cast<long long>(nowMs));
    } else {
        Serial.println();
    }

    Serial.print("[CLOCK] resync_pending=");
    Serial.print(resyncPending ? "yes" : "no");
    if (resyncPending) {
        Serial.print(" left=");
        Serial.println(static_cast<long long>(nextResyncMillis) - static_cast<long long>(nowMs));
    } else {
        Serial.println();
    }

    Serial.print("[CLOCK] last_ntp_ok=");
    Serial.print(lastNtpSyncOk ? "yes" : "no");
    Serial.print(" delta=");
    Serial.println(lastNtpDelta);
    Serial.print("[CLOCK] last_ntp_ms=");
    Serial.println(lastNtpMillis);
    Serial.println("[CLOCK] dump end");
#endif
}



bool SystemClock::updateFromRtc() {
    time_t rtcTime = 0;
    if (!getRtcUtc(rtcTime)) {
        debugLog("RTC read fail");
        return false;
    }

    cachedUtc = rtcTime;
    timeValid = true;
    String msg = "RTC read ok: " + formatIso8601(rtcTime);
    debugLog(msg.c_str());
    logInfo("RTC: zagruzheno validnoe vremia.");
    return true;
}

bool SystemClock::attemptNtpSync(const char* context) {
    if (!ntp) {
        return false;
    }

    ++syncAttemptCounter;
    char dbg[96];
    snprintf(dbg, sizeof(dbg), "NTP try #%lu (%s)", syncAttemptCounter, context ? context : "unknown");
    debugLog(dbg);

    if (!ntp->syncOnce()) {
        snprintf(dbg, sizeof(dbg), "NTP fail ili tajmaut (%s)", context ? context : "unknown");
        debugLog(dbg);
        char warn[96];
        snprintf(warn, sizeof(warn), "NTP: popytka %s - net seti ili tajmaut.", context ? context : "unknown");
        logWarn(warn);
        lastNtpSyncOk = false;
        lastNtpDelta = 0;
        lastNtpMillis = millis();
        return false;
    }

    time_t ntpUtc = 0;
    if (!fetchNtpTime(ntpUtc)) {
        snprintf(dbg, sizeof(dbg), "NTP client ne vernul vremia (%s)", context ? context : "unknown");
        debugLog(dbg);
        char warn[96];
        snprintf(warn, sizeof(warn), "NTP: popytka %s - klient ne vernul vremia.", context ? context : "unknown");
        logWarn(warn);
        lastNtpSyncOk = false;
        lastNtpDelta = 0;
        lastNtpMillis = millis();
        return false;
    }

    if (!isYearValid(ntpUtc)) {
        char warn[96];
        snprintf(warn, sizeof(warn), "NTP: popytka %s - god vne diapazona.", context ? context : "unknown");
        logWarn(warn);
        debugLog("NTP otklonen iz-za goda vne diapazona");
        lastNtpSyncOk = false;
        lastNtpDelta = 0;
        lastNtpMillis = millis();
        return false;
    }

    time_t rtcUtc = 0;
    const bool rtcValid = getRtcUtc(rtcUtc);
    if (rtcValid) {
        const long long deltaCheck = std::llabs(static_cast<long long>(ntpUtc) - static_cast<long long>(rtcUtc));
        if (deltaCheck > SUSPICIOUS_THRESHOLD_SEC) {
            char warn[128];
            snprintf(warn, sizeof(warn), "NTP: popytka %s - podozritelnyi sdvig %llds, otkloneno.", context ? context : "unknown", deltaCheck);
            logWarn(warn);
            debugLog("NTP otkloneno iz-za delta >31d");
            lastNtpSyncOk = false;
            lastNtpDelta = 0;
            lastNtpMillis = millis();
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
            logWarn("RTC: ne udalos zapisat vremia posle NTP.");
            debugLog("RTC zapis ne udalas");
        } else {
            char rtcMsg[96];
            snprintf(rtcMsg, sizeof(rtcMsg), "RTC: obnovleno ot NTP, delta=%llds.", deltaRtc);
            logInfo(rtcMsg);
            debugLog("RTC updated from NTP");
        }
    }

    cachedUtc = ntpUtc;
    timeValid = true;

    long long deltaLog = deltaRtc;
    if (!rtcValid && previousValid) {
        deltaLog = static_cast<long long>(ntpUtc) - static_cast<long long>(previousUtc);
    }

    char info[128];
    snprintf(info, sizeof(info), "NTP: uspeshnaia sinhronizacia (%s), delta=%llds.", context ? context : "unknown", deltaLog);
    logInfo(info);
    snprintf(dbg, sizeof(dbg), "NTP ok delta=%llds", deltaLog);
    debugLog(dbg);

    lastNtpSyncOk = true;
    lastNtpDelta = deltaLog;
    lastNtpMillis = millis();
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
        char dbg[64];
        snprintf(dbg, sizeof(dbg), "schedule retry %lu ms", delayMs);
        debugLog(dbg);
        return;
    }

    nextRetryMillis = millis() + delayMs;
    retryPending = true;
    char dbg[64];
    snprintf(dbg, sizeof(dbg), "schedule retry %lu ms", delayMs);
    debugLog(dbg);
}

void SystemClock::scheduleResync(unsigned long delayMs) {
    if (!ntp) {
        return;
    }

    if (scheduler) {
        scheduler->scheduleDelayed("SystemClockResync", delayMs, [this]() { this->handleResync(); });
        char dbg[64];
        snprintf(dbg, sizeof(dbg), "schedule resync %lu ms", delayMs);
        debugLog(dbg);
        return;
    }

    nextResyncMillis = millis() + delayMs;
    resyncPending = true;
    char dbg[64];
    snprintf(dbg, sizeof(dbg), "schedule resync %lu ms", delayMs);
    debugLog(dbg);
}

void SystemClock::handleRetry() {
    if (attemptNtpSync("scheduled retry")) {
        scheduleResync(NTP_RESYNC_INTERVAL_MS);
    } else {
        scheduleRetry(NTP_RETRY_INTERVAL_MS);
    }
}

void SystemClock::handleResync() {
    if (attemptNtpSync("scheduled resync")) {
        scheduleResync(NTP_RESYNC_INTERVAL_MS);
    } else {
        scheduleRetry(NTP_RETRY_INTERVAL_MS);
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


void SystemClock::debugLog(const char* message) const {
#if GH_CLOCK_DEBUG
    if (!message) {
        return;
    }
    Serial.print("[CLOCK] ");
    Serial.println(message);
#else
    (void)message;
#endif
}
