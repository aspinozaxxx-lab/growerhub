#pragma once
#include <Arduino.h>
#include "Relay.h"

class WaterPump : public Relay {
private:
    unsigned long maxRunTime;
    unsigned long startTime;
    
public:
    WaterPump(int pumpPin, unsigned long maxRunTimeMs = 300000, 
              String pumpName = "Water Pump");
    
    void begin() override;
    void setState(bool state) override;
    bool isPumpRunning();
    bool checkSafety();
};