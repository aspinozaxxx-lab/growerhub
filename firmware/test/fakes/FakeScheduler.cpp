#include "FakeScheduler.h"

#include <utility>

FakeScheduler::FakeScheduler()
    : currentMs(0) {}

unsigned long FakeScheduler::nowMs() const {
    return currentMs;
}

void FakeScheduler::scheduleRepeated(const char* name, unsigned long intervalMs, std::function<void()> callback) {
    delayedTasks.push_back({name, currentMs + intervalMs, std::move(callback)});
}

void FakeScheduler::scheduleDelayed(const char* name, unsigned long delayMs, std::function<void()> callback) {
    delayedTasks.push_back({name, currentMs + delayMs, std::move(callback)});
}

void FakeScheduler::advanceTo(unsigned long ms) {
    currentMs = ms;
}

void FakeScheduler::runDue(unsigned long ms) {
    currentMs = ms;
    std::vector<Task> remaining;
    for (auto& task : delayedTasks) {
        if (task.dueMs <= ms) {
            if (task.callback) {
                task.callback();
            }
        } else {
            remaining.push_back(task);
        }
    }
    delayedTasks.swap(remaining);
}

void FakeScheduler::clear() {
    delayedTasks.clear();
}

const std::vector<FakeScheduler::Task>& FakeScheduler::scheduledOnce() const {
    return delayedTasks;
}
