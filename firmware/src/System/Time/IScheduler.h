#pragma once

#include <functional>

// Интерфейс планировщика задач. В дальнейшем сюда подключим существующий TaskScheduler.
class IScheduler {
public:
    virtual ~IScheduler() = default;

    virtual unsigned long nowMs() const = 0; // Текущий счётчик времени, аналог millis().

    // Планирование периодической задачи; callback вызывается в основном цикле.
    virtual void scheduleRepeated(const char* name, unsigned long intervalMs, std::function<void()> callback) = 0;

    // Планирование единичной задачи с задержкой.
    virtual void scheduleDelayed(const char* name, unsigned long delayMs, std::function<void()> callback) = 0;
};

