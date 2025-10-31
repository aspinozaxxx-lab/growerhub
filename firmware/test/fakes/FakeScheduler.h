#pragma once

#include <functional>
#include <vector>

#include "System/Time/IScheduler.h"

// Фейковый планировщик: хранит отложенные задачи и позволяет тестам запускать их вручную.
class FakeScheduler : public IScheduler {
public:
    struct Task {
        const char* name;
        unsigned long dueMs;
        std::function<void()> callback;
    };

    FakeScheduler();

    unsigned long nowMs() const override;
    void scheduleRepeated(const char* name, unsigned long intervalMs, std::function<void()> callback) override;
    void scheduleDelayed(const char* name, unsigned long delayMs, std::function<void()> callback) override;

    void advanceTo(unsigned long ms);   // Устанавливаем тестовое время.
    void runDue(unsigned long ms);      // Выполняем все задачи с dueMs <= ms.
    void clear();                       // Очищаем очередь задач.

    const std::vector<Task>& scheduledOnce() const;

private:
    unsigned long currentMs;
    std::vector<Task> delayedTasks;
};
