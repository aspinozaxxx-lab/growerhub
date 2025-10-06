#pragma once
#include "Actuator.h"

class Relay : public Actuator {
private:
    int pin;
    bool invertedLogic;
    bool currentState;
    String name;
    
public:
    Relay(int relayPin, bool isInverted = true, String relayName = "Relay");
    void begin() override;
    void setState(bool state) override;
    bool getState() override;
    String getName() override;
    String getStatus() override;
};