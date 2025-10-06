// firmware/src/Sensors/SoilMoistureSensor.h
#pragma once
#include "Sensor.h"

class SoilMoistureSensor : public Sensor {
private:
    int pin;
    int dryValue;
    int wetValue;
    bool available;
    String name;
    unsigned long lastReadTime;
    float lastValue;
    
public:
    SoilMoistureSensor(int sensorPin, int dry = 4095, int wet = 1800, 
                      String sensorName = "Soil Moisture");
    void begin() override;
    float read() override;
    bool isAvailable() override;
    String getName() override;
    String getStatus() override;
    
private:
    int readRaw();
    float convertToPercentage(int rawValue);
};