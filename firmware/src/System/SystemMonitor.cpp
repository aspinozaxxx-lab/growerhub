// firmware/src/System/SystemMonitor.cpp
#include "SystemMonitor.h"

SystemMonitor::SystemMonitor() 
    : startTime(millis()), lastHeapCheck(0), 
      minFreeHeap(UINT32_MAX), maxUsedHeap(0), restartCount(0) {}

void SystemMonitor::begin() {
    Serial.println("SystemMonitor: Started");
    // Здесь можно загрузить restartCount из EEPROM
}

void SystemMonitor::update() {
    checkMemory();
    checkWatchdog();
}

unsigned long SystemMonitor::getUptime() {
    return millis() - startTime;
}

unsigned long SystemMonitor::getFreeHeap() {
    return ESP.getFreeHeap();
}

unsigned long SystemMonitor::getMinFreeHeap() {
    return minFreeHeap;
}

unsigned long SystemMonitor::getMaxUsedHeap() {
    return maxUsedHeap;
}

int SystemMonitor::getRestartCount() {
    return restartCount;
}

float SystemMonitor::getHeapFragmentation() {
    return 100.0 - (ESP.getFreeHeap() * 100.0 / ESP.getHeapSize());
}

String SystemMonitor::getStatus() {
    unsigned long uptime = getUptime();
    unsigned long hours = uptime / 3600000;
    unsigned long minutes = (uptime % 3600000) / 60000;
    unsigned long seconds = (uptime % 60000) / 1000;
    
    return "System[Uptime:" + String(hours) + "h" + String(minutes) + "m" + String(seconds) + "s" +
           ", Heap:" + String(getFreeHeap()) + "/" + String(ESP.getHeapSize()) + 
           " bytes, Fragmentation:" + String(getHeapFragmentation(), 1) + "%]";
}

void SystemMonitor::printSystemInfo() {
    Serial.println("=== System Information ===");
    Serial.println("Chip: ESP32");
    Serial.println("CPU Frequency: " + String(ESP.getCpuFreqMHz()) + " MHz");
    Serial.println("Flash Size: " + String(ESP.getFlashChipSize() / 1024 / 1024) + " MB");
    Serial.println("Free Heap: " + String(ESP.getFreeHeap()) + " bytes");
    Serial.println("Heap Size: " + String(ESP.getHeapSize()) + " bytes");
    Serial.println("Min Free Heap: " + String(minFreeHeap) + " bytes");
    Serial.println("Max Used Heap: " + String(maxUsedHeap) + " bytes");
    Serial.println("Sketch Size: " + String(ESP.getSketchSize()) + " bytes");
    Serial.println("Free Sketch Space: " + String(ESP.getFreeSketchSpace()) + " bytes");
    Serial.println("==========================");
}

void SystemMonitor::checkMemory() {
    if (millis() - lastHeapCheck >= 5000) { // Каждые 5 секунд
        unsigned long freeHeap = getFreeHeap();
        unsigned long usedHeap = ESP.getHeapSize() - freeHeap;
        
        if (freeHeap < minFreeHeap) {
            minFreeHeap = freeHeap;
        }
        if (usedHeap > maxUsedHeap) {
            maxUsedHeap = usedHeap;
        }
        
        lastHeapCheck = millis();
        
        // Предупреждение при низкой памяти
        if (freeHeap < 10000) {
            Serial.println("WARNING: Low heap memory! " + String(freeHeap) + " bytes free");
        }
    }
}

void SystemMonitor::checkWatchdog() {
    // Сбрасываем watchdog
    yield();
}