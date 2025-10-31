#pragma once

#ifndef GH_CLOCK_DEBUG
#define GH_CLOCK_DEBUG 0
#endif

#include <Arduino.h>
#include <ctime>
#include <memory>
#include "System/Time/DS3231RTCAdapter.h"

// Вперёд объявляем интерфейсы DI, чтобы не тянуть тяжёлые заголовки везде.
class IRTC;
class INTPClient;
class ILogger;
class DS3231RTCAdapter;
class IScheduler;

// SystemClock отвечает за единый источник UTC для прошивки.
// Здесь реализована логика синхронизации через NTP с осторожной валидацией.
class SystemClock {
public:
    SystemClock(IRTC* rtcSource, INTPClient* ntpClient, ILogger* logger, IScheduler* scheduler);

    void begin();                     // Инициализация и до 3 синхронных попыток NTP.
    void loop();                      // Обработка отложенных задач, если нет внешнего планировщика.

    bool isTimeSet() const;           // true, если кеш содержит валидный UTC.
    bool nowUtc(time_t& outUtc) const; // Возвращает кеш или RTC, если они валидны.
    String formatIso8601(time_t value) const; // ISO8601 (UTC) для статусов/логов.
    void dumpStatusToSerial();

private:
    bool updateFromRtc();             // Пробуем подтянуть время из RTC.
    bool attemptNtpSync(const char* context); // Запрос к NTP с проверками.
    bool fetchNtpTime(time_t& outUtc);        // Забираем время из клиента после syncOnce.
    bool getRtcUtc(time_t& outUtc) const;     // Возвращаем RTC, если оно валидно.
    bool isYearValid(time_t value) const;     // Проверка диапазона по году.
    void scheduleRetry(unsigned long delayMs);
    void scheduleResync(unsigned long delayMs);
    void handleRetry();
    void handleResync();
    void logInfo(const char* message) const;
    void logWarn(const char* message) const;
    void logError(const char* message) const;
    void debugLog(const char* message) const;

    IRTC* rtc;
    INTPClient* ntp;
    ILogger* logger;
    IScheduler* scheduler;
    std::unique_ptr<DS3231RTCAdapter> internalRtcAdapter;

    mutable time_t cachedUtc;
    mutable bool timeValid;
    unsigned long nextRetryMillis;
    unsigned long nextResyncMillis;
    bool retryPending;
    bool resyncPending;

    unsigned long syncAttemptCounter;
    bool lastNtpSyncOk;
    long long lastNtpDelta;
    unsigned long lastNtpMillis;
    static constexpr const char* TAG = "SystemClock";
    static constexpr unsigned long STARTUP_ATTEMPTS = 3;
    static constexpr unsigned long RETRY_INTERVAL_MS = 30000UL;
    static constexpr unsigned long RESYNC_INTERVAL_MS = 6UL * 60UL * 60UL * 1000UL;
    static constexpr long long MAX_ALLOWED_DRIFT_SECONDS = 31LL * 24LL * 60LL * 60LL;
    static constexpr int MIN_VALID_YEAR = 2025;
    static constexpr int MAX_VALID_YEAR = 2040;
};
