/*
 * Chto v faile: realizaciya zagotovki drivera RTC DS3231.
 * Rol v arhitekture: drivers.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe drivers.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "drivers/rtc/Ds3231Driver.h"

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <ctime>

#include "util/Logger.h"

#if defined(ARDUINO)
#include <Wire.h>
#endif

namespace Drivers {

namespace {
  // I2C adres mikroshemy DS3231.
  const uint8_t kDs3231Address = 0x68;
  // Registr nachala vremeni (sekundy).
  const uint8_t kTimeRegStart = 0x00;
  // Kolichestvo registrov vremeni (sek-min-hour-wday-day-month-year).
  const size_t kTimeRegCount = 7;
  // Pin SDA dlya I2C RTC (ESP32 po umolchaniyu).
  const uint8_t kRtcSdaPin = 21;
  // Pin SCL dlya I2C RTC (ESP32 po umolchaniyu).
  const uint8_t kRtcSclPin = 22;

  // Dekodiruet BCD znachenie v desyatichnoe.
  bool DecodeBcd(uint8_t value, uint8_t* out) {
    if (!out) {
      return false;
    }
    const uint8_t high = static_cast<uint8_t>((value >> 4) & 0x0F);
    const uint8_t low = static_cast<uint8_t>(value & 0x0F);
    if (high > 9 || low > 9) {
      return false;
    }
    *out = static_cast<uint8_t>(high * 10 + low);
    return true;
  }

  // Kodiruet desyatichnoe znachenie v BCD.
  bool EncodeBcd(int value, uint8_t* out) {
    if (!out) {
      return false;
    }
    if (value < 0 || value > 99) {
      return false;
    }
    const uint8_t high = static_cast<uint8_t>(value / 10);
    const uint8_t low = static_cast<uint8_t>(value % 10);
    *out = static_cast<uint8_t>((high << 4) | low);
    return true;
  }

  // Vychislyaet den nedeli (1..7), 1970-01-01 = chetverg, 1=ponedelnik.
  uint8_t CalcWeekday(std::time_t utc_epoch) {
    int64_t days = static_cast<int64_t>(utc_epoch) / 86400LL;
    int64_t wday = (days + 3) % 7;
    if (wday < 0) {
      wday += 7;
    }
    return static_cast<uint8_t>(wday + 1);
  }

  // Chitaet blok registrov po I2C.
  bool ReadRegisters(uint8_t start_reg, uint8_t* out, size_t length) {
    if (!out || length == 0) {
      return false;
    }
#if defined(ARDUINO)
    Wire.beginTransmission(kDs3231Address);
    Wire.write(start_reg);
    if (Wire.endTransmission(false) != 0) {
      return false;
    }
    const size_t received = Wire.requestFrom(kDs3231Address, static_cast<uint8_t>(length));
    if (received != length) {
      return false;
    }
    for (size_t i = 0; i < length; ++i) {
      out[i] = Wire.read();
    }
    return true;
#else
    (void)start_reg;
    return false;
#endif
  }

  // Konvertiruet datu v kolichestvo dney s 1970-01-01 (UTC).
  int64_t DaysFromCivil(int32_t year, uint32_t month, uint32_t day) {
    year -= month <= 2;
    const int32_t era = (year >= 0 ? year : year - 399) / 400;
    const uint32_t yoe = static_cast<uint32_t>(year - era * 400);
    const uint32_t doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
    const uint32_t doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return static_cast<int64_t>(era) * 146097 + static_cast<int64_t>(doe) - 719468;
  }

  // Sobrannyi epoch iz polya vremeni DS3231.
  bool BuildEpoch(uint16_t year,
                  uint8_t month,
                  uint8_t day,
                  uint8_t hour,
                  uint8_t minute,
                  uint8_t second,
                  uint32_t* out_epoch) {
    if (!out_epoch) {
      return false;
    }
    if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || minute > 59 || second > 59) {
      return false;
    }
    const int64_t days = DaysFromCivil(static_cast<int32_t>(year), month, day);
    if (days < 0) {
      return false;
    }
    const int64_t total_seconds =
        days * 86400LL + static_cast<int64_t>(hour) * 3600LL +
        static_cast<int64_t>(minute) * 60LL + static_cast<int64_t>(second);
    if (total_seconds < 0 || total_seconds > static_cast<int64_t>(UINT32_MAX)) {
      return false;
    }
    *out_epoch = static_cast<uint32_t>(total_seconds);
    return true;
  }

  // Rasparslivanie chasa s uchetom 12/24 formata.
  bool ParseHour(uint8_t raw, uint8_t* out_hour) {
    if (!out_hour) {
      return false;
    }
    if (raw & 0x40) {
      const bool pm = (raw & 0x20) != 0;
      uint8_t bcd = 0;
      if (!DecodeBcd(static_cast<uint8_t>(raw & 0x1F), &bcd)) {
        return false;
      }
      if (bcd == 0 || bcd > 12) {
        return false;
      }
      uint8_t hour24 = bcd % 12;
      if (pm) {
        hour24 = static_cast<uint8_t>(hour24 + 12);
      }
      *out_hour = hour24;
      return true;
    }
    return DecodeBcd(static_cast<uint8_t>(raw & 0x3F), out_hour);
  }
}

// Inicializaciya RTC DS3231 po I2C.
bool Ds3231Driver::Init() {
#if defined(ARDUINO)
  if (!Wire.begin(kRtcSdaPin, kRtcSclPin)) {
    Util::Logger::Info("RTC: wire begin fail");
    ready_ = false;
    return false;
  }
  Wire.beginTransmission(kDs3231Address);
  const uint8_t rc = Wire.endTransmission();
  char log_buf[96];
  std::snprintf(log_buf,
                sizeof(log_buf),
                "RTC: i2c probe addr=0x%02X rc=%u (0 - ready)",
                static_cast<unsigned int>(kDs3231Address),
                static_cast<unsigned int>(rc));
  Util::Logger::Info(log_buf);
  ready_ = (rc == 0);
  return ready_;
#else
  ready_ = false;
  return false;
#endif
}

// Zapis epoch vremeni v RTC DS3231.
bool Ds3231Driver::WriteEpoch(std::time_t utc_epoch) {
  if (!ready_) {
    Util::Logger::Info("RTC: write fail not ready");
    return false;
  }
#if defined(ARDUINO)
  if (utc_epoch < 0) {
    Util::Logger::Info("RTC: write fail epoch<0");
    return false;
  }
  std::tm tm_info{};
#if defined(_WIN32)
  if (gmtime_s(&tm_info, &utc_epoch) != 0) {
    Util::Logger::Info("RTC: write fail gmtime");
    return false;
  }
#else
  if (!gmtime_r(&utc_epoch, &tm_info)) {
    Util::Logger::Info("RTC: write fail gmtime");
    return false;
  }
#endif

  const int year = tm_info.tm_year + 1900;
  if (year < 2000 || year > 2099) {
    Util::Logger::Info("RTC: write fail year_range");
    return false;
  }

  uint8_t data[kTimeRegCount]{};
  if (!EncodeBcd(tm_info.tm_sec, &data[0]) ||
      !EncodeBcd(tm_info.tm_min, &data[1]) ||
      !EncodeBcd(tm_info.tm_hour, &data[2]) ||
      !EncodeBcd(tm_info.tm_mday, &data[4]) ||
      !EncodeBcd(tm_info.tm_mon + 1, &data[5]) ||
      !EncodeBcd(year - 2000, &data[6])) {
    Util::Logger::Info("RTC: write fail bcd");
    return false;
  }
  data[3] = CalcWeekday(utc_epoch);

  Wire.beginTransmission(kDs3231Address);
  Wire.write(kTimeRegStart);
  for (size_t i = 0; i < kTimeRegCount; ++i) {
    Wire.write(data[i]);
  }
  const uint8_t rc = Wire.endTransmission();
  if (rc != 0) {
    char log_buf[96];
    std::snprintf(log_buf,
                  sizeof(log_buf),
                  "RTC: write i2c rc=%u.",
                  static_cast<unsigned int>(rc));
    Util::Logger::Info(log_buf);
    return false;
  }
  return true;
#else
  (void)utc_epoch;
  Util::Logger::Info("RTC: write fail no wire");
  return false;
#endif
}

#if defined(UNIT_TEST)
// Testovye obvertki dlya pomoshchnyh funkcii.
bool Ds3231Driver::EncodeBcdForTests(int value, uint8_t* out) {
  return EncodeBcd(value, out);
}

bool Ds3231Driver::DecodeBcdForTests(uint8_t value, uint8_t* out) {
  return DecodeBcd(value, out);
}

uint8_t Ds3231Driver::CalcWeekdayForTests(std::time_t utc_epoch) {
  return CalcWeekday(utc_epoch);
}
#endif

// Chtenie epoch iz RTC DS3231.
bool Ds3231Driver::ReadEpoch(uint32_t* out_epoch) const {
  if (!out_epoch || !ready_) {
    return false;
  }
  uint8_t data[kTimeRegCount]{};
  if (!ReadRegisters(kTimeRegStart, data, kTimeRegCount)) {
    return false;
  }
  uint8_t second = 0;
  uint8_t minute = 0;
  uint8_t hour = 0;
  uint8_t day = 0;
  uint8_t month = 0;
  uint8_t year_bcd = 0;
  if (!DecodeBcd(static_cast<uint8_t>(data[0] & 0x7F), &second)) {
    return false;
  }
  if (!DecodeBcd(static_cast<uint8_t>(data[1] & 0x7F), &minute)) {
    return false;
  }
  if (!ParseHour(data[2], &hour)) {
    return false;
  }
  if (!DecodeBcd(static_cast<uint8_t>(data[4] & 0x3F), &day)) {
    return false;
  }
  if (!DecodeBcd(static_cast<uint8_t>(data[5] & 0x1F), &month)) {
    return false;
  }
  if (!DecodeBcd(data[6], &year_bcd)) {
    return false;
  }
  const uint16_t year = static_cast<uint16_t>(2000 + year_bcd);
  return BuildEpoch(year, month, day, hour, minute, second, out_epoch);
}

}
