#include <cstring>
#include <ctime>
#include <unity.h>

#include "core/Context.h"
#include "services/TimeService.h"
#include "util/Logger.h"

namespace {

// Validnyi epoch dlya 2025-01-01T00:00:00Z.
const std::time_t kValidEpoch2025 = 1735689600;
// Bazovoe znachenie millis dlya testov retry.
const uint32_t kRetryBaseMs = 1000;
// Bazovoe znachenie millis dlya testov resync.
const uint32_t kResyncBaseMs = 2000;

class FakeNtpClient : public Services::INtpClient {
 public:
  // Inicializaciya fake klienta.
  bool Begin() override {
    begin_called = true;
    return true;
  }
  // Sinhronizaciya s fiksirovannym rezultatom.
  bool SyncOnce(uint32_t timeout_ms) override {
    last_timeout_ms = timeout_ms;
    sync_calls++;
    return sync_result;
  }
  // Vozvrat testovogo UTC vremeni.
  bool GetTime(std::time_t& out_utc) const override {
    if (!has_time) {
      return false;
    }
    out_utc = time_value;
    return true;
  }
  // Priznak aktivnoi sinhronizacii (dlya fake = false).
  bool IsSyncInProgress() const override {
    return false;
  }

  bool begin_called = false;
  bool sync_result = false;
  bool has_time = false;
  std::time_t time_value = 0;
  uint32_t last_timeout_ms = 0;
  uint32_t sync_calls = 0;
};

} // namespace

// Proverka prefiksa loga pri otsutstvii sinhronizacii.
void test_logger_prefix_no_watch() {
  Services::TimeService time_service;
  time_service.SetSyncedForTests(false);
  Util::Logger::SetTimeProvider(&time_service);
  Util::Logger::ClearLastMessageForTests();

  Util::Logger::Info("hello");

  const char* line = Util::Logger::GetLastMessageForTests();
  TEST_ASSERT_NOT_NULL(line);
  TEST_ASSERT_TRUE(std::strncmp(line, "[no watch] hello", std::strlen("[no watch] hello")) == 0);
}

// Proverka prefiksa loga pri validnom vremeni.
void test_logger_prefix_synced() {
  Services::TimeService time_service;
  Services::TimeFields fields{2025, 1, 7, 8, 9, 10, 2};
  time_service.SetTimeForTests(fields, 1000);
  Util::Logger::SetTimeProvider(&time_service);
  Util::Logger::ClearLastMessageForTests();

  Util::Logger::Info("hello");

  const char* line = Util::Logger::GetLastMessageForTests();
  TEST_ASSERT_NOT_NULL(line);
  TEST_ASSERT_TRUE(std::strncmp(line, "[07.01 08:09:10] hello", std::strlen("[07.01 08:09:10] hello")) == 0);
}

// Proverka planirovaniya retry posle fail sync.
void test_time_service_retry_schedule_on_fail() {
  Services::TimeService time_service;
  FakeNtpClient ntp;
  ntp.sync_result = false;
  ntp.has_time = false;

  time_service.SetNtpClientForTests(&ntp);
  time_service.SetNowMsForTests(kRetryBaseMs);

  Core::Context ctx{};
  time_service.Init(ctx);

  TEST_ASSERT_EQUAL_UINT32(3, ntp.sync_calls);
  TEST_ASSERT_EQUAL_UINT32(5000, ntp.last_timeout_ms);
  TEST_ASSERT_TRUE(time_service.IsRetryPendingForTests());
  TEST_ASSERT_FALSE(time_service.IsResyncPendingForTests());
  TEST_ASSERT_EQUAL_UINT32(kRetryBaseMs + 30000, time_service.GetNextRetryMsForTests());
}

// Proverka planirovaniya resync posle uspeshnogo sync.
void test_time_service_resync_schedule_on_success() {
  Services::TimeService time_service;
  FakeNtpClient ntp;
  ntp.sync_result = true;
  ntp.has_time = true;
  ntp.time_value = kValidEpoch2025;

  time_service.SetNtpClientForTests(&ntp);
  time_service.SetNowMsForTests(kResyncBaseMs);

  Core::Context ctx{};
  time_service.Init(ctx);

  TEST_ASSERT_EQUAL_UINT32(1, ntp.sync_calls);
  TEST_ASSERT_EQUAL_UINT32(5000, ntp.last_timeout_ms);
  TEST_ASSERT_FALSE(time_service.IsRetryPendingForTests());
  TEST_ASSERT_TRUE(time_service.IsResyncPendingForTests());
  TEST_ASSERT_EQUAL_UINT32(kResyncBaseMs + 21600000, time_service.GetNextResyncMsForTests());
}


