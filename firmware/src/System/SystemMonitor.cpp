// firmware/src/System/SystemMonitor.cpp
#include "SystemMonitor.h"

SystemMonitor::SystemMonitor() 
    : startTime(millis()), lastHeapCheck(0), 
      minFreeHeap(UINT32_MAX), maxUsedHeap(0), restartCount(0),
      pumpStatusProvider(nullptr),
      ackPublisher(nullptr),
      statePublisher(nullptr),
      rebooter(&defaultRebooter)
#ifdef UNIT_TEST
      , lastRebootCalled_(false)
#endif
{}

void SystemMonitor::begin() {
    Serial.println("SystemMonitor: Started");
    // ����� ����� ����㧨�� restartCount �� EEPROM
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

bool SystemMonitor::rebootIfSafe(const String& reason, const String& correlationId) {
    const bool pumpRunning = pumpStatusProvider ? pumpStatusProvider() : false;
    const char* statusText = pumpRunning ? "running" : "idle";
    const String reasonText = reason.length() > 0 ? reason : String("unspecified");

    if (pumpRunning) {
        Serial.println(F("[SYS] reboot declined: pump running"));
        if (ackPublisher && correlationId.length() > 0) {
            ackPublisher(correlationId, statusText, false);
        }
        return false;
    }

    Serial.print(F("[SYS] reboot accepted: "));
    Serial.println(reasonText);
    if (ackPublisher && correlationId.length() > 0) {
        ackPublisher(correlationId, statusText, true);
    }

    if (statePublisher) {
        statePublisher(false);
    }

    const unsigned long waitStart = millis();
    // Zdes' horosho by pokrutit' MQTT loop, no prjamogo dostupa net, poetomu zhdy pauzu.
    while (millis() - waitStart < REBOOT_GRACE_DELAY_MS) {
        delay(1);
    }

    if (rebooter) {
#ifdef UNIT_TEST
        lastRebootCalled_ = true;
#endif
        rebooter->restart();
    }

    return true;
}

void SystemMonitor::checkMemory() {
    if (millis() - lastHeapCheck >= 5000) { // ����� 5 ᥪ㭤
        unsigned long freeHeap = getFreeHeap();
        unsigned long usedHeap = ESP.getHeapSize() - freeHeap;
        
        if (freeHeap < minFreeHeap) {
            minFreeHeap = freeHeap;
        }
        if (usedHeap > maxUsedHeap) {
            maxUsedHeap = usedHeap;
        }
        
        lastHeapCheck = millis();
        
        // �।�०����� �� ������ �����
        if (freeHeap < 10000) {
            Serial.println("WARNING: Low heap memory! " + String(freeHeap) + " bytes free");
        }
    }
}

void SystemMonitor::checkWatchdog() {
    // ����뢠�� watchdog
    yield();
}

void SystemMonitor::setPumpStatusProvider(std::function<bool(void)> fn) {
    pumpStatusProvider = fn;
}

void SystemMonitor::setAckPublisher(std::function<void(const String&, const char*, bool)> fn) {
    ackPublisher = fn;
}

void SystemMonitor::setStatePublisher(std::function<void(bool)> fn) {
    statePublisher = fn;
}

void SystemMonitor::setRebooter(IRebooter* r) {
    rebooter = r ? r : &defaultRebooter;
}
