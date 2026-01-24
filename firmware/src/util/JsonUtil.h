/*
 * Chto v faile: obyavleniya struktur konfiguracii i JSON kodirovaniya/dekodirovaniya.
 * Rol v arhitekture: util.
 * Naznachenie: publichnyi API i tipy dlya sloya util.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdint>

namespace Util {

// Versiya JSON shemy scenariev.
static const uint32_t kScenariosSchemaVersion = 2;
// Versiya JSON shemy Wi-Fi konfiguracii.
static const uint32_t kWifiSchemaVersion = 1;
// Maksimalnoe kolichestvo pochvennyh datchikov.
static const size_t kMaxSoilSensors = 2;
// Maksimalnoe kolichestvo zapisey poliva po raspisaniyu.
static const size_t kMaxWaterScheduleEntries = 4;
// Maksimalnoe kolichestvo zapisey sveta po raspisaniyu.
static const size_t kMaxLightScheduleEntries = 4;

struct MoistureSensorConfig {
  // Nomer porta datchika.
  uint8_t port;
  // Flag vklyucheniya datchika.
  bool enabled;
  // Porog vlazhnosti v procentah.
  uint8_t threshold_percent;
  // Dlitelnost poliva v sekundah.
  uint32_t duration_s;
};

struct WaterByMoistureConfig {
  // Flag vklyucheniya avtomatiki po vlazhnosti.
  bool enabled;
  // Minimalnyi interval mezhdu polivami.
  uint32_t min_time_between_watering_s;
  // Konfiguraciya datchikov vlazhnosti.
  MoistureSensorConfig sensors[kMaxSoilSensors];
};

struct WaterScheduleEntry {
  // Start vremeni v formate HHMM.
  uint16_t start_hhmm;
  // Dlitelnost poliva v sekundah.
  uint16_t duration_s;
  // Maska dnei nedeli.
  uint8_t days_mask;
};

struct WaterByScheduleConfig {
  // Flag vklyucheniya poliva po raspisaniyu.
  bool enabled;
  // Chislo aktivnyh zapisey.
  size_t entry_count;
  // Spisok zapisey poliva.
  WaterScheduleEntry entries[kMaxWaterScheduleEntries];
};

struct LightScheduleEntry {
  // Start vremeni v formate HHMM.
  uint16_t start_hhmm;
  // Konets vremeni v formate HHMM.
  uint16_t end_hhmm;
  // Maska dnei nedeli.
  uint8_t days_mask;
};

struct LightByScheduleConfig {
  // Flag vklyucheniya sveta po raspisaniyu.
  bool enabled;
  // Chislo aktivnyh zapisey.
  size_t entry_count;
  // Spisok zapisey sveta.
  LightScheduleEntry entries[kMaxLightScheduleEntries];
};

struct ScenariosConfig {
  // Versiya shemy konfiguracii.
  uint32_t schema_version;
  // Nastroyki poliva po vlazhnosti.
  WaterByMoistureConfig water_moisture;
  // Nastroyki poliva po raspisaniyu.
  WaterByScheduleConfig water_schedule;
  // Nastroyki sveta po raspisaniyu.
  LightByScheduleConfig light_schedule;
};

/**
 * Sozdaet defoltnuyu konfiguraciyu scenariev.
 */
ScenariosConfig DefaultScenariosConfig();
/**
 * Kodiruet konfiguraciyu scenariev v JSON.
 * @param config Vhodnaya konfiguraciya scenariev.
 * @param out Bufer dlya JSON stroki.
 * @param out_size Razmer bufera v baytah.
 */
bool EncodeScenariosConfig(const ScenariosConfig& config, char* out, size_t out_size);
/**
 * Dekodiruet konfiguraciyu scenariev iz JSON.
 * @param json JSON stroka s konfiguraciei.
 * @param config Vyhodnaya konfiguraciya scenariev.
 */
bool DecodeScenariosConfig(const char* json, ScenariosConfig* config);
/**
 * Proveryaet korrektnost konfiguracii scenariev.
 * @param config Konfiguraciya dlya proverki.
 */
bool ValidateScenariosConfig(const ScenariosConfig& config);
/**
 * Kodiruet Wi-Fi konfiguraciyu v JSON.
 * @param ssid SSID seti dlya sohraneniya.
 * @param password Parol seti (mozhet byt pustym).
 * @param out Bufer dlya JSON stroki.
 * @param out_size Razmer bufera v baytah.
 */
bool EncodeWifiConfig(const char* ssid, const char* password, char* out, size_t out_size);
/**
 * Kodiruet spisok Wi-Fi setei v JSON.
 * @param ssids Massiv SSID.
 * @param passwords Massiv paroley (mozhet byt null dlya pustogo parolya).
 * @param count Kolichestvo setei.
 * @param out Bufer dlya JSON stroki.
 * @param out_size Razmer bufera v baytah.
 */
bool EncodeWifiConfig(const char* const* ssids,
                      const char* const* passwords,
                      size_t count,
                      char* out,
                      size_t out_size);
/**
 * Proveryaet korrektnost JSON Wi-Fi konfiguracii.
 * @param json JSON stroka s konfiguraciei Wi-Fi.
 */
bool ValidateWifiConfig(const char* json);

}
