#pragma once

#include <ctime>
#include <cstdint>
#include <cstddef>
#include "System/Time/IRTC.h"

#ifndef GH_CLOCK_DEBUG
#define GH_CLOCK_DEBUG 0
#endif

// Adapter RTC dlia mikroshemy DS3231 (I2C, adres 0x68).
class DS3231RTCAdapter : public IRTC {
public:
    DS3231RTCAdapter();

    bool begin() override;                      // Inicializacia I2C i proverka ustroistva.
    bool isTimeValid() const override;          // Proverka poslednego prochitannogo vremeni.
    bool getTime(time_t& outUtc) const override;// Vozvrashchaem keshirovannoe znachenie.
    bool setTime(time_t utc) override;          // Zapisivaem UTC v registry DS3231.
    void dumpRegisters() const;                 // Vyvodit znachenia bazovyh registrov (debug).

private:
    bool readTime(time_t& outUtc) const;        // Chtenie tekushchego vremeni iz chipa.
    bool writeTime(time_t utc);                 // Vnutrenniaia zapis bez keshirivania.

    bool readRegister(uint8_t reg, uint8_t& value) const;
    bool readRegisters(uint8_t startReg, uint8_t* data, size_t length) const;
    bool writeRegister(uint8_t reg, uint8_t value) const;
    bool writeRegisters(uint8_t startReg, const uint8_t* data, size_t length) const;

    static bool decodeBCD(uint8_t value, uint8_t& out);
    static uint8_t encodeBCD(uint8_t value);

    mutable time_t cachedUtc_;
    mutable bool valid_;
    mutable bool expectValidAfterWrite_;
};
