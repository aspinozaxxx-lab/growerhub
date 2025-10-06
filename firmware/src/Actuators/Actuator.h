#pragma once
#include <Arduino.h>

class Actuator {
public:
    virtual void begin() = 0;
    virtual void setState(bool state) = 0;
    virtual bool getState() = 0;
    virtual String getName() = 0;
    virtual String getStatus() = 0;
    virtual ~Actuator() {}
};