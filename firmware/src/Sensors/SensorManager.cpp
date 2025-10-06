// firmware/src/Sensors/SensorManager.cpp
#include "SensorManager.h"

SensorManager::SensorManager() : lastReadTime(0), soilMoistureSensor(nullptr), dht22Sensor(nullptr) {}

SensorManager::~SensorManager() {
    for (auto sensor : sensors) {
        delete sensor;
    }
    sensors.clear();
}

void SensorManager::begin() {
    for (auto sensor : sensors) {
        sensor->begin();
    }
    findSpecialSensors();
    lastReadTime = millis();
}

void SensorManager::update() {
    if (millis() - lastReadTime >= READ_INTERVAL) {
        readAllSensors();
        lastReadTime = millis();
    }
}

void SensorManager::addSensor(Sensor* sensor) {
    sensors.push_back(sensor);
}

void SensorManager::addSoilMoistureSensor(SoilMoistureSensor* sensor) {
    sensors.push_back(sensor);
    soilMoistureSensor = sensor;
}

void SensorManager::addDHT22Sensor(DHT22Sensor* sensor) {
    sensors.push_back(sensor);
    dht22Sensor = sensor;
}

SoilMoistureSensor* SensorManager::getSoilMoistureSensor() {
    return soilMoistureSensor;
}

DHT22Sensor* SensorManager::getDHT22Sensor() {
    return dht22Sensor;
}

String SensorManager::getStatus() {
    String status = "SensorManager[" + String(sensors.size()) + " sensors]:\n";
    for (auto sensor : sensors) {
        status += "  " + sensor->getStatus() + "\n";
    }
    return status;
}

void SensorManager::printDiagnostics() {
    Serial.println("=== Sensor Diagnostics ===");
    for (auto sensor : sensors) {
        Serial.println(sensor->getStatus());
    }
    Serial.println("==========================");
}

void SensorManager::readAllSensors() {
    for (auto sensor : sensors) {
        sensor->read(); // Обновляем показания
    }
}

void SensorManager::findSpecialSensors() {
    // Альтернатива dynamic_cast - проверка по имени
    for (auto sensor : sensors) {
        String name = sensor->getName();
        name.toLowerCase();
        
        if (name.indexOf("soil") >= 0 || name.indexOf("moisture") >= 0) {
            soilMoistureSensor = (SoilMoistureSensor*)sensor;
        }
        else if (name.indexOf("dht") >= 0) {
            dht22Sensor = (DHT22Sensor*)sensor;
        }
    }
}