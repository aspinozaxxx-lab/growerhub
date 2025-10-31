#pragma once

#include <cstdint>

// Независимый шим millis() для юнит-тестов SystemClock.
namespace FakeMillis {
    void set(unsigned long value);    // Устанавливаем текущее «время» в мс.
    unsigned long get();              // Возвращаем текущее значение.
    void advance(unsigned long delta); // Продвигаем время на delta мс.
}

// Глобальная подмена millis(), чтобы код прошивки читал тестовое время.
unsigned long millis();

