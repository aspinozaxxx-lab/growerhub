#include "DS3231RTCAdapter.h"

#if defined(ESP_PLATFORM) || defined(ARDUINO)

#include <Arduino.h>
#include <Wire.h>
#include <cstdio>

namespace {
    constexpr uint8_t DS3231_ADDRESS = 0x68;
    constexpr uint8_t REG_SECONDS = 0x00;
    constexpr uint8_t REG_MINUTES = 0x01;
    constexpr uint8_t REG_HOURS = 0x02;
    constexpr uint8_t REG_DAY = 0x03;
    constexpr uint8_t REG_DATE = 0x04;
    constexpr uint8_t REG_MONTH_CENTURY = 0x05;
    constexpr uint8_t REG_YEAR = 0x06;
    constexpr uint8_t REG_CONTROL = 0x0E;
    constexpr uint8_t REG_STATUS = 0x0F;

    constexpr int MIN_YEAR = 2025;
    constexpr int MAX_YEAR = 2040;

    bool isYearValid(int year) { return year >= MIN_YEAR && year <= MAX_YEAR; }

    time_t makeTimeUtc(const tm& timeinfo) {
    #if defined(ESP_PLATFORM)
        tm copy = timeinfo;
        return mktime(&copy);
    #else
        tm copy = timeinfo;
        return _mkgmtime(&copy);
    #endif
    }

    void tmFromUtc(time_t utc, tm& out) {
    #if defined(ESP_PLATFORM)
        gmtime_r(&utc, &out);
    #else
        const tm* tmp = gmtime(&utc);
        if (tmp) { out = *tmp; }
    #endif
    }

#if GH_CLOCK_DEBUG
    void debugPrint(const char* text) {
        if (!text) {
            return;
        }
        Serial.println(text);
    }

    void debugPrintf(const char* fmt, uint8_t value) {
        char buffer[48];
        snprintf(buffer, sizeof(buffer), fmt, value);
        Serial.println(buffer);
    }
#endif
}

DS3231RTCAdapter::DS3231RTCAdapter()
    : cachedUtc_(0),
      valid_(false),
      expectValidAfterWrite_(false) {}

bool DS3231RTCAdapter::begin() {
    Wire.begin(21, 22); // TODO: vynesti SDA/SCL v nastroyki esli poiaviatsia.
#if GH_CLOCK_DEBUG
    bool rtcOnBus = false;
    for (uint8_t address = 0x03; address <= 0x77; ++address) {
        Wire.beginTransmission(address);
        const int scanStatus = Wire.endTransmission();
        if (scanStatus == 0) {
            debugPrintf("[CLOCK] I2C dev @0x%02X found", address);
            if (address == DS3231_ADDRESS) {
                rtcOnBus = true;
            }
        }
    }
    if (!rtcOnBus) {
        debugPrint("[CLOCK] I2C no dev @0x68");
    }
#endif

    uint8_t dummy = 0;
    if (!readRegister(REG_SECONDS, dummy)) {
#if GH_CLOCK_DEBUG
        debugPrint("[CLOCK] RTC init fail (ne dostupen po I2C)");
#endif
        valid_ = false;
        return false;
    }

    uint8_t control = 0;
    uint8_t status = 0;
    if (!readRegister(REG_CONTROL, control) || !readRegister(REG_STATUS, status)) {
        valid_ = false;
        return false;
    }

    const bool eoscBit = (control & 0x80) != 0;
    if (eoscBit) {
        uint8_t updatedControl = static_cast<uint8_t>(control & ~0x80);
        if (!writeRegister(REG_CONTROL, updatedControl)) {
            valid_ = false;
            return false;
        }
        control = updatedControl;
#if GH_CLOCK_DEBUG
        debugPrint("[CLOCK] EOSC=1 byl sbroshen (generator zapushchen)");
#endif
    }

    if ((status & 0x80) != 0) {
#if GH_CLOCK_DEBUG
        debugPrint("[CLOCK] OSF=1 (vozmozhna razriazhena/otsutstvuet batareika VBAT ili chasy stoiat)");
#endif
    }

    time_t rtcUtc = 0;
    if (readTime(rtcUtc)) {
        tm timeinfo{};
        tmFromUtc(rtcUtc, timeinfo);
        valid_ = isYearValid(timeinfo.tm_year + 1900);
        if (valid_) {
            cachedUtc_ = rtcUtc;
        }
    } else {
        valid_ = false;
    }

    expectValidAfterWrite_ = false;
    return true;
}

bool DS3231RTCAdapter::isTimeValid() const {
    if (!valid_) {
        time_t rtcUtc = 0;
        if (readTime(rtcUtc)) {
            tm timeinfo{};
            tmFromUtc(rtcUtc, timeinfo);
            valid_ = isYearValid(timeinfo.tm_year + 1900);
            if (valid_) {
                cachedUtc_ = rtcUtc;
            }
        }
    }
    return valid_;
}

bool DS3231RTCAdapter::getTime(time_t& outUtc) const {
    if (!isTimeValid()) {
        return false;
    }
    outUtc = cachedUtc_;
    return true;
}

bool DS3231RTCAdapter::setTime(time_t utc) {
    if (!writeTime(utc)) {
        return false;
    }
    cachedUtc_ = utc;
    tm info{};
    tmFromUtc(cachedUtc_, info);
    valid_ = isYearValid(info.tm_year + 1900);
    expectValidAfterWrite_ = true;
    return true;
}

bool DS3231RTCAdapter::readTime(time_t& outUtc) const {
    uint8_t raw[7] = {0};
    if (!readRegisters(REG_SECONDS, raw, sizeof(raw))) {
        if (expectValidAfterWrite_) {
#if GH_CLOCK_DEBUG
            debugPrint("[CLOCK] RTC read fail posle zapisi - prover'te VBAT i derzhatel CR2032");
#endif
            expectValidAfterWrite_ = false;
        }
        return false;
    }

    uint8_t seconds = 0;
    uint8_t minutes = 0;
    uint8_t hours = 0;
    uint8_t dayOfMonth = 0;
    uint8_t monthValue = 0;
    uint8_t yearValue = 0;

    const bool hour12Mode = (raw[2] & 0x40) != 0;
    bool ok = true;

    ok &= decodeBCD(static_cast<uint8_t>(raw[0] & 0x7F), seconds) && seconds < 60;
    ok &= decodeBCD(static_cast<uint8_t>(raw[1] & 0x7F), minutes) && minutes < 60;

    if (hour12Mode) {
        uint8_t hour12 = 0;
        ok &= decodeBCD(static_cast<uint8_t>(raw[2] & 0x1F), hour12) && hour12 >= 1 && hour12 <= 12;
        const bool pm = (raw[2] & 0x20) != 0;
        hours = static_cast<uint8_t>(hour12 % 12);
        if (pm) {
            hours = static_cast<uint8_t>(hours + 12);
        }
    } else {
        ok &= decodeBCD(static_cast<uint8_t>(raw[2] & 0x3F), hours) && hours < 24;
    }

    ok &= decodeBCD(static_cast<uint8_t>(raw[4] & 0x3F), dayOfMonth) && dayOfMonth >= 1 && dayOfMonth <= 31;
    ok &= decodeBCD(static_cast<uint8_t>(raw[5] & 0x1F), monthValue) && monthValue >= 1 && monthValue <= 12;
    const bool century = (raw[5] & 0x80) != 0;
    ok &= decodeBCD(raw[6], yearValue);

    if (!ok) {
#if GH_CLOCK_DEBUG
        debugPrint("[CLOCK] RTC read fail (format)");
#endif
        if (expectValidAfterWrite_) {
#if GH_CLOCK_DEBUG
            debugPrint("[CLOCK] RTC read fail posle zapisi - prover'te VBAT i derzhatel CR2032");
#endif
            expectValidAfterWrite_ = false;
        }
        return false;
    }

    int fullYear = 2000 + static_cast<int>(yearValue);
    if (century) {
        fullYear += 100;
    }
    if (!isYearValid(fullYear)) {
#if GH_CLOCK_DEBUG
        debugPrint("[CLOCK] RTC read fail (year)");
#endif
        if (expectValidAfterWrite_) {
#if GH_CLOCK_DEBUG
            debugPrint("[CLOCK] RTC read fail posle zapisi - prover'te VBAT i derzhatel CR2032");
#endif
            expectValidAfterWrite_ = false;
        }
        return false;
    }

    tm timeinfo{};
    timeinfo.tm_sec = static_cast<int>(seconds);
    timeinfo.tm_min = static_cast<int>(minutes);
    timeinfo.tm_hour = static_cast<int>(hours);
    timeinfo.tm_mday = static_cast<int>(dayOfMonth);
    timeinfo.tm_mon = static_cast<int>(monthValue) - 1;
    timeinfo.tm_year = fullYear - 1900;
    timeinfo.tm_isdst = 0;
    const uint8_t dowRaw = static_cast<uint8_t>(raw[3] & 0x07);
    if (dowRaw != 0) {
        timeinfo.tm_wday = static_cast<int>(dowRaw % 7);
    }

    time_t utc = makeTimeUtc(timeinfo);
    if (utc <= 0) {
#if GH_CLOCK_DEBUG
        debugPrint("[CLOCK] RTC read fail (epoch)");
#endif
        if (expectValidAfterWrite_) {
#if GH_CLOCK_DEBUG
            debugPrint("[CLOCK] RTC read fail posle zapisi - prover'te VBAT i derzhatel CR2032");
#endif
            expectValidAfterWrite_ = false;
        }
        return false;
    }

    expectValidAfterWrite_ = false;
    outUtc = utc;
    return true;
}

bool DS3231RTCAdapter::writeTime(time_t utc) {
    tm timeinfo{};
    tmFromUtc(utc, timeinfo);
    const int fullYear = timeinfo.tm_year + 1900;
    if (!isYearValid(fullYear)) {
        return false;
    }

    uint8_t buffer[7];
    buffer[0] = encodeBCD(static_cast<uint8_t>(timeinfo.tm_sec % 60));
    buffer[1] = encodeBCD(static_cast<uint8_t>(timeinfo.tm_min % 60));
    buffer[2] = encodeBCD(static_cast<uint8_t>(timeinfo.tm_hour % 24));
    buffer[2] &= 0x3F;
    const uint8_t weekday = static_cast<uint8_t>(timeinfo.tm_wday == 0 ? 7 : timeinfo.tm_wday);
    buffer[3] = encodeBCD(weekday);
    buffer[4] = encodeBCD(static_cast<uint8_t>(timeinfo.tm_mday));
    uint8_t monthEncoded = encodeBCD(static_cast<uint8_t>(timeinfo.tm_mon + 1));
    if (fullYear >= 2100) {
        monthEncoded = static_cast<uint8_t>(monthEncoded | 0x80);
    }
    buffer[5] = monthEncoded;
    buffer[6] = encodeBCD(static_cast<uint8_t>((fullYear - 2000) % 100));

    if (!writeRegisters(REG_SECONDS, buffer, sizeof(buffer))) {
        return false;
    }

    uint8_t status = 0;
    if (readRegister(REG_STATUS, status)) {
        if ((status & 0x80) != 0) {
            const uint8_t cleared = static_cast<uint8_t>(status & ~0x80);
            if (writeRegister(REG_STATUS, cleared)) {
#if GH_CLOCK_DEBUG
                debugPrint("[CLOCK] RTC: obnovili vremia, OSF sbrosen");
#endif
            }
        } else {
#if GH_CLOCK_DEBUG
            debugPrint("[CLOCK] RTC: obnovili vremia, OSF uzhe 0");
#endif
        }
    }

    return true;
}

void DS3231RTCAdapter::dumpRegisters() const {
#if GH_CLOCK_DEBUG
    uint8_t control = 0;
    uint8_t status = 0;
    uint8_t seconds = 0;
    if (readRegister(REG_CONTROL, control) && readRegister(REG_STATUS, status) && readRegister(REG_SECONDS, seconds)) {
        char msg[96];
        snprintf(msg, sizeof(msg), "[CLOCK] CTRL=0x%02X STAT=0x%02X SEC=0x%02X (OSF=%c EOSC=%c)",
                 static_cast<unsigned int>(control),
                 static_cast<unsigned int>(status),
                 static_cast<unsigned int>(seconds),
                 (status & 0x80) ? '1' : '0',
                 (control & 0x80) ? '1' : '0');
        Serial.println(msg);
    } else {
        debugPrint("[CLOCK] RTC dump regs fail");
    }
#endif
}

bool DS3231RTCAdapter::readRegister(uint8_t reg, uint8_t& value) const {
    Wire.beginTransmission(DS3231_ADDRESS);
    Wire.write(reg);
    const int status = Wire.endTransmission(false);
    if (status != 0) {
#if GH_CLOCK_DEBUG
        char msg[64];
        snprintf(msg, sizeof(msg), "[CLOCK] RTC read fail (i2c error=%d)", status);
        debugPrint(msg);
#endif
        return false;
    }

    const uint8_t received = Wire.requestFrom(DS3231_ADDRESS, static_cast<uint8_t>(1));
    if (received != 1) {
#if GH_CLOCK_DEBUG
        char msg[64];
        snprintf(msg, sizeof(msg), "[CLOCK] RTC read fail (bytes=%u)", received);
        debugPrint(msg);
#endif
        return false;
    }

    value = static_cast<uint8_t>(Wire.read());
    return true;
}

bool DS3231RTCAdapter::readRegisters(uint8_t startReg, uint8_t* data, size_t length) const {
    if (!data || length == 0) {
        return false;
    }

    Wire.beginTransmission(DS3231_ADDRESS);
    Wire.write(startReg);
    const int status = Wire.endTransmission(false);
    if (status != 0) {
#if GH_CLOCK_DEBUG
        char msg[64];
        snprintf(msg, sizeof(msg), "[CLOCK] RTC read fail (i2c error=%d)", status);
        debugPrint(msg);
#endif
        return false;
    }

    const uint8_t received = Wire.requestFrom(DS3231_ADDRESS, static_cast<uint8_t>(length));
    if (received != length) {
#if GH_CLOCK_DEBUG
        char msg[64];
        snprintf(msg, sizeof(msg), "[CLOCK] RTC read fail (bytes=%u)", received);
        debugPrint(msg);
#endif
        return false;
    }

    for (size_t i = 0; i < length && Wire.available(); ++i) {
        data[i] = static_cast<uint8_t>(Wire.read());
    }
    return true;
}

bool DS3231RTCAdapter::writeRegister(uint8_t reg, uint8_t value) const {
    Wire.beginTransmission(DS3231_ADDRESS);
    Wire.write(reg);
    Wire.write(value);
    const int status = Wire.endTransmission();
    if (status != 0) {
#if GH_CLOCK_DEBUG
        char msg[64];
        snprintf(msg, sizeof(msg), "[CLOCK] RTC write fail (i2c error=%d)", status);
        debugPrint(msg);
#endif
        return false;
    }
    return true;
}

bool DS3231RTCAdapter::writeRegisters(uint8_t startReg, const uint8_t* data, size_t length) const {
    if (!data || length == 0) {
        return false;
    }

    Wire.beginTransmission(DS3231_ADDRESS);
    Wire.write(startReg);
    for (size_t i = 0; i < length; ++i) {
        Wire.write(data[i]);
    }
    const int status = Wire.endTransmission();
    if (status != 0) {
#if GH_CLOCK_DEBUG
        char msg[64];
        snprintf(msg, sizeof(msg), "[CLOCK] RTC write fail (i2c error=%d)", status);
        debugPrint(msg);
#endif
        return false;
    }
    return true;
}

bool DS3231RTCAdapter::decodeBCD(uint8_t value, uint8_t& out) {
    const uint8_t tens = static_cast<uint8_t>((value >> 4) & 0x0F);
    const uint8_t units = static_cast<uint8_t>(value & 0x0F);
    if (tens > 9 || units > 9) {
        return false;
    }
    out = static_cast<uint8_t>(tens * 10 + units);
    return true;
}

uint8_t DS3231RTCAdapter::encodeBCD(uint8_t value) {
    return static_cast<uint8_t>(((value / 10) << 4) | (value % 10));
}

#else

DS3231RTCAdapter::DS3231RTCAdapter() : cachedUtc_(0), valid_(false), expectValidAfterWrite_(false) {}

bool DS3231RTCAdapter::begin() { valid_ = false; return false; }
bool DS3231RTCAdapter::isTimeValid() const { return false; }
bool DS3231RTCAdapter::getTime(time_t& outUtc) const { outUtc = 0; return false; }
bool DS3231RTCAdapter::setTime(time_t) { return false; }
bool DS3231RTCAdapter::readTime(time_t&) const { return false; }
bool DS3231RTCAdapter::writeTime(time_t) { return false; }
void DS3231RTCAdapter::dumpRegisters() const {}
bool DS3231RTCAdapter::readRegister(uint8_t, uint8_t&) const { return false; }
bool DS3231RTCAdapter::readRegisters(uint8_t, uint8_t*, size_t) const { return false; }
bool DS3231RTCAdapter::writeRegister(uint8_t, uint8_t) const { return false; }
bool DS3231RTCAdapter::writeRegisters(uint8_t, const uint8_t*, size_t) const { return false; }
bool DS3231RTCAdapter::decodeBCD(uint8_t, uint8_t&) { return false; }
uint8_t DS3231RTCAdapter::encodeBCD(uint8_t value) { return value; }

#endif
