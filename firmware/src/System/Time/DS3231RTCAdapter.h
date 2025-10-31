#pragma once

#include <ctime>
#include <cstdint>
#include "System/Time/IRTC.h"

// Адаптер RTC для микросхемы DS3231 (I²C, адрес 0x68).
class DS3231RTCAdapter : public IRTC {
public:
    DS3231RTCAdapter();

    bool begin() override;                 // Инициализация I²C и проверка устройства.
    bool isTimeValid() const override;     // Проверка последнего прочитанного времени.
    bool getTime(time_t& outUtc) const override; // Возвращаем кешированное значение.
    bool setTime(time_t utc) override;     // Записываем UTC в регистры DS3231.

private:
    bool readTime(time_t& outUtc) const;   // Чтение текущего времени из чипа.
    bool writeTime(time_t utc);            // Внутренняя запись без кеширования.

    static uint8_t encodeBCD(uint8_t value);
    static uint8_t decodeBCD(uint8_t value);

    mutable time_t cachedUtc_;
    mutable bool valid_;
};


