#include "DS3231RTCAdapter.h"

#if defined(ESP_PLATFORM) || defined(ARDUINO)

#include <Arduino.h>
#include <Wire.h>

namespace {
    constexpr uint8_t DS3231_ADDRESS = 0x68;
    constexpr uint8_t REG_SECONDS = 0x00;
    constexpr uint8_t REG_MINUTES = 0x01;
    constexpr uint8_t REG_HOURS = 0x02;
    constexpr uint8_t REG_DAY = 0x03;
    constexpr uint8_t REG_DATE = 0x04;
    constexpr uint8_t REG_MONTH_CENTURY = 0x05;
    constexpr uint8_t REG_YEAR = 0x06;

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
}

DS3231RTCAdapter::DS3231RTCAdapter() : cachedUtc_(0), valid_(false) {}

bool DS3231RTCAdapter::begin() {
    Wire.begin(21, 22); // TODO: вынести SDA/SCL в настройки, если появятся.
    Wire.beginTransmission(DS3231_ADDRESS);
    if (Wire.endTransmission() != 0) {
        valid_ = false;
        return false;
    }

    time_t rtcUtc = 0;
    if (readTime(rtcUtc)) {
        tm timeinfo{};
        tmFromUtc(rtcUtc, timeinfo);
        valid_ = isYearValid(timeinfo.tm_year + 1900);
        if (valid_) {
            cachedUtc_ = rtcUtc;
        }
    }
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
    return true;
}

bool DS3231RTCAdapter::readTime(time_t& outUtc) const {
    Wire.beginTransmission(DS3231_ADDRESS);
    Wire.write(REG_SECONDS);
    if (Wire.endTransmission() != 0) {
        return false;
    }

    constexpr uint8_t bytesToRead = 7;
    if (Wire.requestFrom(DS3231_ADDRESS, bytesToRead) != bytesToRead) {
        return false;
    }

    const uint8_t seconds = decodeBCD(Wire.read() & 0x7F);
    const uint8_t minutes = decodeBCD(Wire.read() & 0x7F);
    const uint8_t hours = decodeBCD(Wire.read() & 0x3F);
    Wire.read(); // day of week, игнорируем
    const uint8_t day = decodeBCD(Wire.read() & 0x3F);
    const uint8_t monthCentury = Wire.read();
    const uint8_t month = decodeBCD(monthCentury & 0x1F);
    const bool century = (monthCentury & 0x80) != 0;
    const uint8_t year = decodeBCD(Wire.read());

    tm timeinfo{};
    timeinfo.tm_sec = seconds;
    timeinfo.tm_min = minutes;
    timeinfo.tm_hour = hours;
    timeinfo.tm_mday = day;
    timeinfo.tm_mon = month ? static_cast<int>(month) - 1 : 0;
    timeinfo.tm_year = (century ? 200 : 190) + year - 1900;

    if (!isYearValid(timeinfo.tm_year + 1900)) {
        return false;
    }

    outUtc = makeTimeUtc(timeinfo);
    return true;
}

bool DS3231RTCAdapter::writeTime(time_t utc) {
    tm timeinfo{};
    tmFromUtc(utc, timeinfo);
    const int fullYear = timeinfo.tm_year + 1900;

    Wire.beginTransmission(DS3231_ADDRESS);
    Wire.write(REG_SECONDS);
    Wire.write(encodeBCD(static_cast<uint8_t>(timeinfo.tm_sec)));
    Wire.write(encodeBCD(static_cast<uint8_t>(timeinfo.tm_min)));
    Wire.write(encodeBCD(static_cast<uint8_t>(timeinfo.tm_hour)));
    Wire.write(encodeBCD(static_cast<uint8_t>(timeinfo.tm_wday ? timeinfo.tm_wday : 7)));
    Wire.write(encodeBCD(static_cast<uint8_t>(timeinfo.tm_mday)));

    const bool century = fullYear >= 2000;
    const uint8_t monthValue = encodeBCD(static_cast<uint8_t>(timeinfo.tm_mon + 1)) | (century ? 0x80 : 0x00);
    Wire.write(monthValue);
    Wire.write(encodeBCD(static_cast<uint8_t>(fullYear % 100)));

    return Wire.endTransmission() == 0;
}

uint8_t DS3231RTCAdapter::encodeBCD(uint8_t value) {
    return static_cast<uint8_t>(((value / 10) << 4) | (value % 10));
}

uint8_t DS3231RTCAdapter::decodeBCD(uint8_t value) {
    return static_cast<uint8_t>(((value >> 4) * 10) + (value & 0x0F));
}

#else

DS3231RTCAdapter::DS3231RTCAdapter() : cachedUtc_(0), valid_(false) {}

bool DS3231RTCAdapter::begin() { valid_ = false; return false; }
bool DS3231RTCAdapter::isTimeValid() const { return false; }
bool DS3231RTCAdapter::getTime(time_t& outUtc) const { outUtc = 0; return false; }
bool DS3231RTCAdapter::setTime(time_t) { return false; }
bool DS3231RTCAdapter::readTime(time_t&) const { return false; }
bool DS3231RTCAdapter::writeTime(time_t) { return false; }
uint8_t DS3231RTCAdapter::encodeBCD(uint8_t value) { return value; }
uint8_t DS3231RTCAdapter::decodeBCD(uint8_t value) { return value; }

#endif
