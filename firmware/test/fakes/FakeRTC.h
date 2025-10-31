#pragma once

#include "System/Time/IRTC.h"

// Простая фейковая реализация RTC для тестов SystemClock.
class FakeRTC : public IRTC {
public:
    FakeRTC();

    bool begin() override;
    bool isTimeValid() const override;
    bool getTime(time_t& outUtc) const override;
    bool setTime(time_t utc) override;

    void setCurrent(time_t value); // Ручная установка времени.
    void setValid(bool value);     // Управление признаком валидности.

    time_t lastSetTime() const;

private:
    time_t current;
    bool valid;
    time_t lastSet;
};

