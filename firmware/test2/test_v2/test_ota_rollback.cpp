#include <unity.h>

#include "core/Context.h"
#include "services/StorageService.h"
#include "services/ota/OtaRollback.h"

static bool g_rollback_called = false;

static void RollbackHook() {
  g_rollback_called = true;
}

void test_ota_pending_confirm() {
  Services::StorageService storage;
  storage.SetRootForTests("test_storage_ota1");
  Core::Context ctx{};
  storage.Init(ctx);

  Services::OtaRollback rollback;
  rollback.Init(&storage);
  rollback.SetRollbackHandler(&RollbackHook);

  rollback.MarkPending(1000);
  rollback.OnBoot(1000);
  TEST_ASSERT_TRUE(rollback.IsPending());

  rollback.ConfirmBoot();
  TEST_ASSERT_FALSE(rollback.IsPending());
}

void test_ota_boot_failures_trigger() {
  Services::StorageService storage;
  storage.SetRootForTests("test_storage_ota2");
  Core::Context ctx{};
  storage.Init(ctx);

  Services::OtaRollback rollback;
  rollback.Init(&storage);
  rollback.SetRollbackHandler(&RollbackHook);

  g_rollback_called = false;
  rollback.MarkPending(0);
  rollback.OnBoot(0);
  rollback.OnBoot(0);
  rollback.OnBoot(0);

  TEST_ASSERT_TRUE(rollback.IsRollbackRequested());
  TEST_ASSERT_TRUE(g_rollback_called);
}

void test_ota_timeout_trigger() {
  Services::StorageService storage;
  storage.SetRootForTests("test_storage_ota3");
  Core::Context ctx{};
  storage.Init(ctx);

  Services::OtaRollback rollback;
  rollback.Init(&storage);
  rollback.SetRollbackHandler(&RollbackHook);

  g_rollback_called = false;
  rollback.MarkPending(1000);
  rollback.OnBoot(1000);
  rollback.OnBoot(401500);

  TEST_ASSERT_TRUE(rollback.IsRollbackRequested());
  TEST_ASSERT_TRUE(g_rollback_called);
}
