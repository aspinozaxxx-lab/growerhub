#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace Core {

struct Context;

class Scheduler {
 public:
  using TaskCallback = void (*)(Context& ctx, uint32_t now_ms);

  bool AddPeriodic(const char* name, uint32_t interval_ms, TaskCallback callback);
  void Tick(Context& ctx, uint32_t now_ms);
  size_t Count() const;

 private:
  struct Task {
    const char* name;
    uint32_t interval_ms;
    uint32_t last_run_ms;
    TaskCallback callback;
    bool active;
  };

  static const size_t kMaxTasks = 8;

  std::array<Task, kMaxTasks> tasks_;
  size_t count_ = 0;
};

}
