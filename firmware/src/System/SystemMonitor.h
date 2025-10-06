// firmware/src/System/SystemMonitor.h
#pragma once
#include <Arduino.h>

class SystemMonitor {
private:
    unsigned long startTime;
    unsigned long lastHeapCheck;
    unsigned long minFreeHeap;
    unsigned long maxUsedHeap;
    int restartCount;
    
public:
    SystemMonitor();
    
    void begin();
    void update();
    
    unsigned long getUptime();
    unsigned long getFreeHeap();
    unsigned long getMinFreeHeap();
    unsigned long getMaxUsedHeap();
    int getRestartCount();
    float getHeapFragmentation();
    
    String getStatus();
    void printSystemInfo();
    
private:
    void checkMemory();
    void checkWatchdog();
};