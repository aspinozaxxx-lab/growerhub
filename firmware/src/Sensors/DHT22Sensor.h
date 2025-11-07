// firmware/src/Sensors/DHT22Sensor.h
#pragma once
#include "Sensor.h"
#include <DHT.h>

class DHT22Sensor : public Sensor {
private:
    DHT* dht;
    int pin;
    bool available;
    String name;
    float lastTemperature;
    float lastHumidity;
    unsigned long lastReadTime;
    
public:
    DHT22Sensor(int sensorPin, String sensorName = "DHT22");
    ~DHT22Sensor();
    void begin() override;
    float read() override; // Возвращает влажность воздуха
    float readTemperature(); // Дополнительный метод для температуры
    bool isAvailable() override;
    bool isAvailable() const; // Vozvrashaet poslednij status dostupnosti datchika.
    String getName() override;
    String getStatus() override;
    
private:
    bool readSensor();
};
