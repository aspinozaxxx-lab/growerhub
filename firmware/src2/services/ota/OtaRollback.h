#pragma once

#include <cstdint>

namespace Services {
class StorageService;
}

namespace Services {

class OtaRollback {
 public:
  struct State {
    uint32_t schema_version;
    bool pending;
    uint8_t boot_fail_count;
    uint64_t pending_since_ms;
  };

  using RollbackHandler = void (*)();

  void Init(StorageService* storage);
  void SetRollbackHandler(RollbackHandler handler);
  void MarkPending(uint64_t now_ms);
  void OnBoot(uint64_t now_ms);
  void ConfirmBoot();
  bool IsPending() const;
  bool IsRollbackRequested() const;

#if defined(UNIT_TEST)
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
