/*
 * Chto v faile: obyavleniya servisa vremeni i vremennyh metok.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include <ctime>
#include <memory>

#include "core/Context.h"
#include "services/time/INtpClient.h"

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

class IRtcProvider {
 public:
  /**
   * Virtualnyi destruktor RTC-providera.
   */
  virtual ~IRtcProvider() = default;
  /**
   * Vozvrashaet UTC vremya iz RTC.
   * @param out_utc Vyhodnoe UTC vremya.
   */
  virtual bool GetUtc(std::time_t& out_utc) const = 0;
};

class TimeService {
 public:
  /**
   * Init servisa vremeni.
   * @param ctx Kontekst (dlya sootvetstviya interfeisu).
   */
  void Init(Core::Context& ctx);
  /**
   * Periodicheskiy loop dlya NTP-retry/resync.
   * @param ctx Kontekst s zavisimostyami servisa.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void Loop(Core::Context& ctx, uint32_t now_ms);
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
   * Vozvrashaet unix ms tolko pri validnom vremeni.
   * @param out_ms Vyhodnoe unix vremya v ms.
   */
  bool TryGetUnixTimeMs(uint64_t* out_ms) const;
  /**
   * Proveryaet, sinhronizirovano li vremya.
   */
  bool IsSynced() const;
  /**
   * Priznak nalichiya validnogo vremeni iz lyubogo istochnika.
   */
  bool HasValidTime() const;
  /**
   * Formiruet stroku vremeni dlya loga (DD.MM hh:mm:ss).
   * @param out Bufer dlya zapisi.
   * @param out_size Razmer bufera.
   */
  bool GetLogTimestamp(char* out, size_t out_size) const;

  /**
   * Ustanavlivaet RTC provider dlya runtime.
   * @param rtc RTC provider.
   */
  void SetRtcProvider(IRtcProvider* rtc);

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
  /**
   * Podmena NTP klienta dlya testov.
   * @param client NTP klient dlya testov.
   */
  void SetNtpClientForTests(INtpClient* client);
  /**
   * Podmena RTC providera dlya testov.
   * @param rtc RTC provider dlya testov.
   */
  void SetRtcProviderForTests(IRtcProvider* rtc);
  /**
   * Podmena tekushchego millis dlya testov.
   * @param now_ms Znachenie millis.
   */
  void SetNowMsForTests(uint32_t now_ms);
  /**
   * Priznak zaplanirovannogo retry dlya testov.
   */
  bool IsRetryPendingForTests() const;
  /**
   * Priznak zaplanirovannogo resync dlya testov.
   */
  bool IsResyncPendingForTests() const;
  /**
   * Vremya sleduyushchego retry dlya testov.
   */
  uint32_t GetNextRetryMsForTests() const;
  /**
   * Vremya sleduyushchego resync dlya testov.
   */
  uint32_t GetNextResyncMsForTests() const;
#endif

 private:
  /**
   * Zapuskaet odnu popytku NTP sinhronizacii.
   * @param context Metka konteksta dlya loga.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  bool AttemptNtpSync(const char* context, uint32_t now_ms);
  /**
   * Poluchaet vremya iz NTP klienta.
   * @param out_utc Vyhodnoe UTC vremya.
   */
  bool FetchNtpTime(std::time_t& out_utc) const;
  /**
   * Poluchaet vremya iz RTC, esli dostupno.
   * @param out_utc Vyhodnoe UTC vremya.
   */
  bool GetRtcUtc(std::time_t& out_utc) const;
  /**
   * Proveryaet dopustimost goda v UTC.
   * @param value UTC vremya.
   */
  bool IsYearValid(std::time_t value) const;
  /**
   * Proveryaet podklyuchenie Wi-Fi.
   */
  bool IsWifiConnected() const;
  /**
   * Vozvrashaet tekushchee millis s uchetom testov.
   */
  uint32_t GetNowMs() const;
  /**
   * Poluchaet luchshee UTC vremya (system ili RTC).
   * @param out_utc Vyhodnoe UTC vremya.
   */
  bool TryGetBestUtc(std::time_t& out_utc) const;
  /**
   * Validaciya RTC epoch po minimalnomu porogu.
   * @param value UTC vremya.
   */
  bool IsRtcEpochValid(std::time_t value) const;
  /**
   * Planiruet povtornuyu sinhronizaciyu (retry).
   * @param now_ms Tekuschee vremya v millisekundah.
   * @param reason Metka prichiny dlya loga.
   */
  void ScheduleRetry(uint32_t now_ms, const char* reason);
  /**
   * Planiruet periodicheskiy resync.
   * @param now_ms Tekuschee vremya v millisekundah.
   * @param reason Metka prichiny dlya loga.
   */
  void ScheduleResync(uint32_t now_ms, const char* reason);

  INtpClient* ntp_ = nullptr;
  std::unique_ptr<INtpClient> owned_ntp_;
  IRtcProvider* rtc_ = nullptr;
  std::time_t cached_utc_ = 0;
  bool time_valid_ = false;
  uint32_t next_retry_ms_ = 0;
  uint32_t next_resync_ms_ = 0;
  bool retry_pending_ = false;
  bool resync_pending_ = false;
  uint32_t sync_attempt_counter_ = 0;
  bool last_sync_ok_ = false;
  int64_t last_sync_delta_sec_ = 0;
  uint32_t last_sync_ms_ = 0;
  // Kolichestvo popytok progрева RTC.
  uint8_t rtc_warmup_attempts_ = 0;
  // Vremya sleduyushchey popytki progрева RTC.
  uint32_t rtc_warmup_next_try_ms_ = 0;
  // Flag zaversheniya progрева RTC.
  bool rtc_warmup_done_ = false;
#if defined(UNIT_TEST)
  bool test_synced_ = false;
  TimeFields test_fields_{};
  uint64_t test_unix_ms_ = 0;
  uint32_t test_now_ms_ = 0;
#endif
};

}


