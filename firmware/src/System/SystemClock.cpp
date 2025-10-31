#include "SystemClock.h"

#include "System/Time/IRTC.h"
#include "System/Time/INTPClient.h"
#include "System/Time/ILogger.h"
#include "System/Time/IScheduler.h"

SystemClock::SystemClock(IRTC* rtcSource, INTPClient* ntpClient, ILogger* loggerInstance, IScheduler* schedulerInstance)
    : rtc(rtcSource),
      ntp(ntpClient),
      logger(loggerInstance),
      scheduler(schedulerInstance),
      cachedUtc(0),
      timeValid(false) {}

void SystemClock::begin() {
    // Стартуем RTC и NTP, если они есть, но не навязываем синхронизацию.
    if (rtc) {
        rtc->begin();
        if (updateFromRtc()) {
            return;
        }
    }

    if (ntp) {
        ntp->begin();
        // Позже добавим попытку первой синхронизации. Пока просто фиксируем запуск.
        if (updateFromNtp()) {
            return;
        }
    }

    timeValid = false;
}

void SystemClock::loop() {
    // Будущие ретраи/планировщик попадут сюда. Пока ничего не делаем.
    (void)scheduler;
}

bool SystemClock::isTimeSet() const {
    if (rtc && rtc->isTimeValid()) {
        return true;
    }
    return timeValid;
}

bool SystemClock::nowUtc(time_t& outUtc) const {
    if (rtc && rtc->isTimeValid()) {
        time_t rtcTime = 0;
        if (rtc->getTime(rtcTime)) {
            cachedUtc = rtcTime;
            timeValid = true;
            outUtc = rtcTime;
            return true;
        }
    }

    if (timeValid && cachedUtc > 0) {
        outUtc = cachedUtc;
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
    if (!rtc) {
        return false;
    }

    if (!rtc->isTimeValid()) {
        logWarn("RTC доступен, но время невалидно.");
        return false;
    }

    time_t rtcTime = 0;
    if (!rtc->getTime(rtcTime)) {
        logWarn("RTC не вернул время.");
        return false;
    }

    cachedUtc = rtcTime;
    timeValid = (rtcTime > 0);

    if (timeValid) {
        logInfo("Время получено из RTC.");
    }
    return timeValid;
}

bool SystemClock::updateFromNtp() {
    if (!ntp) {
        return false;
    }

    time_t ntpTime = 0;
    if (!ntp->getTime(ntpTime)) {
        logWarn("NTP клиент не вернул время.");
        return false;
    }

    if (ntpTime <= 0) {
        logWarn("NTP вернул некорректное значение.");
        return false;
    }

    cachedUtc = ntpTime;
    timeValid = true;
    logInfo("Время получено от NTP.");

    if (rtc) {
        rtc->setTime(ntpTime);
    }

    return true;
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

