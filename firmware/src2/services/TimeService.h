/*
 * Chto v faile: obyavleniya servisa vremeni i vremennyh metok.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

#include "core/Context.h"

namespace Services {

struct TimeFields {
  // God.
  uint16_t year;
  // Mesyats (1-12).
  uint8_t month;
  // Den mesyaca (1-31).
  uint8_t day;
  // Chas (0-23).
  uint8_t hour;
  // Minuta (0-59).
  uint8_t minute;
  // Sekunda (0-59).
  uint8_t second;
  // Den nedeli (0-6, gde 0 = voskresenie).
  uint8_t wday;
};

class TimeService {
 public:
  /**
   * Init servisa vremeni.
   * @param ctx Kontekst (dlya sootvetstviya interfeisu).
   */
  void Init(Core::Context& ctx);
  /**
   * Poluchaet tekushchee vremya v polya.
   * @param out Vyhodnaya struktura s polyami vremeni.
   */
  bool GetTime(TimeFields* out) const;
  /**
   * Vozvrashaet tekuschee vremya v unix ms.
   */
  uint64_t GetUnixTimeMs() const;
  /**
   * Proveryaet, sinhronizirovano li vremya.
   */
  bool IsSynced() const;

#if defined(UNIT_TEST)
  /**
   * Zadanie vremeni dlya testov.
   * @param fields Polya vremeni dlya testov.
   * @param unix_ms Unix vremya v ms.
   */
  void SetTimeForTests(const TimeFields& fields, uint64_t unix_ms);
  /**
   * Ustanavlivaet flag sinhronizacii dlya testov.
   * @param synced Flag sinhronizacii.
   */
  void SetSyncedForTests(bool synced);
#endif

 private:
#if defined(UNIT_TEST)
  bool test_synced_ = false;
  TimeFields test_fields_{};
  uint64_t test_unix_ms_ = 0;
#endif
};

}
