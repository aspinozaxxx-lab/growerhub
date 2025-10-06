// firmware/src/Sensors/SoilMoistureSensor.cpp
#include "SoilMoistureSensor.h"

SoilMoistureSensor::SoilMoistureSensor(int sensorPin, int dry, int wet, String sensorName) 
    : pin(sensorPin), dryValue(dry), wetValue(wet), name(sensorName), 
      available(false), lastReadTime(0), lastValue(0.0) {}

void SoilMoistureSensor::begin() {
    pinMode(pin, INPUT);
    available = true;
    lastReadTime = millis();
}

float SoilMoistureSensor::read() {
    if (!available) return -1.0;
    
    int rawValue = readRaw();
    lastValue = convertToPercentage(rawValue);
    lastReadTime = millis();
    
    return lastValue;
}

bool SoilMoistureSensor::isAvailable() {
    return available;
}

String SoilMoistureSensor::getName() {
    return name;
}

String SoilMoistureSensor::getStatus() {
    String status = "SoilMoisture[pin:" + String(pin) + 
                   ", value:" + String(lastValue) + 
                   "%, available:" + String(available) + "]";
    return status;
}

int SoilMoistureSensor::readRaw() {
    // Усреднение нескольких измерений для стабильности
    long sum = 0;
    for(int i = 0; i < 10; i++) {
        sum += analogRead(pin);
        delay(10);
    }
    return sum / 10;
}

float SoilMoistureSensor::convertToPercentage(int rawValue) {
    // Конвертация RAW значения в проценты (0-100%)
    rawValue = constrain(rawValue, wetValue, dryValue);
    float percentage = 100.0 * (dryValue - rawValue) / (dryValue - wetValue);
    return constrain(percentage, 0.0, 100.0);
}