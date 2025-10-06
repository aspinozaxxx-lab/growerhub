#pragma once
#include "Actuator.h"
#include "Relay.h"
#include "WaterPump.h"
#include <vector>

class ActuatorManager {
private:
    std::vector<Actuator*> actuators;
    WaterPump* waterPump;
    Relay* lightRelay;
    
public:
    ActuatorManager();
    ~ActuatorManager();
    
    void begin();
    void update();
    
    void addWaterPump(WaterPump* pump);
    void addLightRelay(Relay* relay);
    
    void setWaterPumpState(bool state);
    void setLightState(bool state);
    
    bool isWaterPumpRunning();
    bool isLightOn();
    
    String getStatus();
};