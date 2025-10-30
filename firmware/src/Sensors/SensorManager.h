// firmware/src/Sensors/SensorManager.h
#pragma once
#include "Sensor.h"
#include "SoilMoistureSensor.h"
#include "DHT22Sensor.h"
#include <vector>

class SensorManager {
private:
    std::vector<Sensor*> sensors;
    SoilMoistureSensor* soilMoistureSensor;
    DHT22Sensor* dht22Sensor;
    unsigned long lastReadTime;
    const unsigned long READ_INTERVAL = 5000; // 5 секунд
    
public:
    SensorManager();
    ~SensorManager();
    
    void begin();
    void update();
    void addSensor(Sensor* sensor);
    void addSoilMoistureSensor(SoilMoistureSensor* sensor);
    void addDHT22Sensor(DHT22Sensor* sensor);
    
    // Получение конкретных сенсоров
    SoilMoistureSensor* getSoilMoistureSensor();
    DHT22Sensor* getDHT22Sensor();
    
    // Статус и диагностика
    String getStatus();
    void printDiagnostics();
    
private:
    void readAllSensors();
    void findSpecialSensors();
};
