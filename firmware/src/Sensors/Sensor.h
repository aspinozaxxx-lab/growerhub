// firmware/src/Sensors/Sensor.h
#pragma once
#include <Arduino.h>

class Sensor {
public:
    virtual void begin() = 0;
    virtual float read() = 0;
    virtual bool isAvailable() = 0;
    virtual String getName() = 0;
    virtual String getStatus() = 0;
    virtual ~Sensor() {}
};