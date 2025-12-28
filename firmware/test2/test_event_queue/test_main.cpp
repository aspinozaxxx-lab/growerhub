#include <unity.h>

#include "core/EventQueue.h"

void setUp() {}
void tearDown() {}

static void test_event_queue_order() {
  Core::EventQueue queue;
  Core::Event first{Core::EventType::kCustom, 1};
  Core::Event second{Core::EventType::kCustom, 2};

  TEST_ASSERT_TRUE(queue.Push(first));
  TEST_ASSERT_TRUE(queue.Push(second));
  TEST_ASSERT_EQUAL(2, static_cast<int>(queue.Size()));

  Core::Event out{};
  TEST_ASSERT_TRUE(queue.Pop(out));
  TEST_ASSERT_EQUAL_UINT32(1, out.value);

  TEST_ASSERT_TRUE(queue.Pop(out));
  TEST_ASSERT_EQUAL_UINT32(2, out.value);
  TEST_ASSERT_EQUAL(0, static_cast<int>(queue.Size()));
}

int main(int argc, char** argv) {
  (void)argc;
  (void)argv;
  UNITY_BEGIN();
  RUN_TEST(test_event_queue_order);
  return UNITY_END();
}
