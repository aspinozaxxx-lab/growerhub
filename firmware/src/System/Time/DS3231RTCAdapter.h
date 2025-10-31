#pragma once

#include <ctime>
#include <cstdint>
#include "System/Time/IRTC.h"

// Adapter RTC dlia mikroshemy DS3231 (I2C, adres 0x68).
class DS3231RTCAdapter : public IRTC {
public:
    DS3231RTCAdapter();

    bool begin() override;                 // Inicializacia I2C i proverka ustroistva.
    bool isTimeValid() const override;     // Proverka poslednego prochitannogo vremeni.
    bool getTime(time_t& outUtc) const override; // Vozvrashchaem keshirovannoe znachenie.
    bool setTime(time_t utc) override;     // Zapisivaem UTC v registry DS3231.

private:
    bool readTime(time_t& outUtc) const;   // Chtenie tekushchego vremeni iz chipa.
    bool writeTime(time_t utc);            // Vnutrenniaia zapis bez keshirivania.

    static uint8_t encodeBCD(uint8_t value);
    static uint8_t decodeBCD(uint8_t value);

    mutable time_t cachedUtc_;
    mutable bool valid_;
};
