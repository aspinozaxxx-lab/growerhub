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

// Legacy versiya, gde file s setyami polnostyu zamenyal builtin defaults.
static const uint32_t kWifiLegacySchemaVersion = 1;
// Tekushchaya versiya s yavnym itogovym spiskom setei.
static const uint32_t kWifiSchemaVersion = 2;
/**
 * Proveryaet podderzhivaemuyu versiyu Wi-Fi konfiguracii.
 */
bool IsSupportedWifiSchemaVersion(uint32_t schema_version);
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
