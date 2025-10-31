#pragma once

#include <Arduino.h>
#include <ctime>

// Вперёд объявляем интерфейсы DI, чтобы не тянуть тяжёлые заголовки везде.
class IRTC;
class INTPClient;
class ILogger;
class IScheduler;

// SystemClock отвечает за единый источник UTC для прошивки.
// Пока это каркас: интеграция NTP/RTC появится позже, но API уже готов.
class SystemClock {
public:
    SystemClock(IRTC* rtcSource, INTPClient* ntpClient, ILogger* logger, IScheduler* scheduler);

    void begin();                     // Инициализация зависимостей (RTC/NTP), без обязательной синхронизации.
    void loop();                      // Крючок под будущие периодические задачи.

    bool isTimeSet() const;           // true, если удалось получить надёжный UTC.
    bool nowUtc(time_t& outUtc) const; // Возвращает текущее UTC, если доступно.
    String formatIso8601(time_t value) const; // ISO8601 (UTC) для статусов/логов.

private:
    bool updateFromRtc();             // Пробуем подтянуть время из RTC.
    bool updateFromNtp();             // Пробуем подтянуть время из NTP (будет реализовано позднее).
    void logInfo(const char* message) const;
    void logWarn(const char* message) const;

    IRTC* rtc;
    INTPClient* ntp;
    ILogger* logger;
    IScheduler* scheduler;

    mutable time_t cachedUtc;
    mutable bool timeValid;

    static constexpr const char* TAG = "SystemClock";
};

