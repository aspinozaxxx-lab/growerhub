#include "WaterPump.h"

WaterPump::WaterPump(int pumpPin, unsigned long maxRunTimeMs, String pumpName)
    : Relay(pumpPin, true, pumpName), maxRunTime(maxRunTimeMs), startTime(0) {}

void WaterPump::begin() {
    Relay::begin(); // Вызываем родительский begin
    startTime = 0;
}

void WaterPump::setState(bool state) {
    // Просто вызываем родительский метод + логика времени
    Relay::setState(state);
    
    if (state) {
        startTime = millis(); // Запоминаем время включения
    } else {
        startTime = 0; // Сбрасываем при выключении
    }
}

bool WaterPump::isPumpRunning() {
    return Relay::getState(); // Состояние насоса = состояние реле
}

bool WaterPump::checkSafety() {
    if (Relay::getState() && startTime > 0 && (millis() - startTime > maxRunTime)) {
        Relay::setState(false); // Выключаем через родителя
        startTime = 0;
        return false;
    }
    return true;
}
