#include <unity.h>

#include "core/EventQueue.h"

void test_event_queue_order() {
  Core::EventQueue queue;
  Core::Event first{Core::EventType::kCustom, 1};
  Core::Event second{Core::EventType::kCustom, 2};

  TEST_ASSERT_TRUE(queue.Push(first));
  TEST_ASSERT_TRUE(queue.Push(second));
  TEST_ASSERT_EQUAL(2, static_cast<int>(queue.Size()));

  Core::Event out{};
  TEST_ASSERT_TRUE(queue.Pop(out));
  TEST_ASSERT_EQUAL_UINT64(1, out.value);

  TEST_ASSERT_TRUE(queue.Pop(out));
  TEST_ASSERT_EQUAL_UINT64(2, out.value);
  TEST_ASSERT_EQUAL(0, static_cast<int>(queue.Size()));
}
