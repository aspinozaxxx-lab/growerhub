#include "core/Scheduler.h"
#include "core/Context.h"

namespace Core {

bool Scheduler::AddPeriodic(const char* name, uint32_t interval_ms, TaskCallback callback) {
  if (callback == nullptr || interval_ms == 0 || count_ >= kMaxTasks) {
    return false;
  }
  tasks_[count_] = Task{name, interval_ms, 0, callback, true};
  ++count_;
  return true;
}

void Scheduler::Tick(Context& ctx, uint32_t now_ms) {
  for (size_t i = 0; i < count_; ++i) {
    Task& task = tasks_[i];
    if (!task.active || task.callback == nullptr) {
      continue;
    }
    if ((now_ms - task.last_run_ms) >= task.interval_ms) {
      task.last_run_ms = now_ms;
      task.callback(ctx, now_ms);
    }
  }
}

size_t Scheduler::Count() const {
  return count_;
}

}
