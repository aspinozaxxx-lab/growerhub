#pragma once

#include <vector>
#include <functional>
#include "System/Time/IScheduler.h"

// �������� �����������: ��������� ������ � ��������� ����� ��������� �� �������.
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

    void advanceTo(unsigned long ms);           // ��������� �������� �������.
    void runDue(unsigned long ms);              // ��������� ��� ������, ���� ������� <= ms.
    void clear();

    const std::vector<Task>& scheduledOnce() const;

private:
    unsigned long currentMs;
    std::vector<Task> delayedTasks;
};
