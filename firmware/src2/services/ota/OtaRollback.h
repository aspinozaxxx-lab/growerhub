/*
 * Chto v faile: obyavleniya hraneniya sostoyaniya OTA i logiki rollback.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstdint>

namespace Services {
class StorageService;
}

namespace Services {

class OtaRollback {
 public:
  struct State {
    // Versiya shemy sostoyaniya.
    uint32_t schema_version;
    // Flag pending dlya podtverzhdeniya OTA.
    bool pending;
    // Schetchik neudachnyh zagruzok.
    uint8_t boot_fail_count;
    // Vremya pending v ms (unix).
    uint64_t pending_since_ms;
  };

  // Tip callback dlya rollback deistviya.
  using RollbackHandler = void (*)();

  /**
   * Init rollback sostoyaniya iz hranilishcha.
   * @param storage Ukazatel na servis hranilishcha.
   */
  void Init(StorageService* storage);
  /**
   * Ustanavlivaet callback dlya rollback.
   * @param handler Ukazatel na funkciyu rollback.
   */
  void SetRollbackHandler(RollbackHandler handler);
  /**
   * Pomechaet proshivku kak pending.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void MarkPending(uint64_t now_ms);
  /**
   * Obrabotka sostoyaniya pri starte.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnBoot(uint64_t now_ms);
  /**
   * Podtverzhdaet uspeshnyy boot.
   */
  void ConfirmBoot();
  /**
   * Proveryaet, est li pending sostoyanie.
   */
  bool IsPending() const;
  /**
   * Proveryaet, zaproshen li rollback.
   */
  bool IsRollbackRequested() const;

#if defined(UNIT_TEST)
  /**
   * Vozvrashaet tekuschee sostoyanie dlya testov.
   */
  const State& GetState() const;
#endif

 private:
  bool LoadState();
  bool SaveState() const;
  void RequestRollback();

  static const uint32_t kSchemaVersion = 1;
  static const uint8_t kMaxBootFailures = 3;
  static const uint64_t kPendingTimeoutMs = 300000;
  static const char* kStatePath;

  StorageService* storage_ = nullptr;
  RollbackHandler rollback_handler_ = nullptr;
  State state_{};
  bool rollback_requested_ = false;
};

}
