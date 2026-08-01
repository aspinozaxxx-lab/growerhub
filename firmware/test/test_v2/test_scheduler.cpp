#include <unity.h>

#include "core/Context.h"
#include "core/EventQueue.h"
#include "core/Scheduler.h"

static int g_calls = 0;

static void TickTask(Core::Context& ctx, uint32_t now_ms) {
  (void)ctx;
  (void)now_ms;
  ++g_calls;
}

void test_scheduler_periodic() {
  Core::Scheduler scheduler;
  Core::EventQueue queue;
  Core::Context ctx{&scheduler, &queue, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr};

  g_calls = 0;
  bool added = scheduler.AddPeriodic("tick", 5, TickTask);
  TEST_ASSERT_TRUE(added);

  scheduler.Tick(ctx, 0);
  scheduler.Tick(ctx, 5);
  scheduler.Tick(ctx, 9);
  scheduler.Tick(ctx, 10);

  TEST_ASSERT_EQUAL(2, g_calls);
}
