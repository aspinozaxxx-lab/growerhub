#pragma once

#include <ctime>

// Абстракция аппаратных RTC (DS3231 и т.п.).
class IRTC {
public:
    virtual ~IRTC() = default;

    virtual bool begin() = 0;                 // Подготовка чипа.
    virtual bool isTimeValid() const = 0;     // true, если в RTC хранится надёжное UTC.
    virtual bool getTime(time_t& outUtc) const = 0; // Чтение UTC (секунды с 1970).
    virtual bool setTime(time_t utc) = 0;     // Обновление времени из NTP/host.
};

