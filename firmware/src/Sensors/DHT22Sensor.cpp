// firmware/src/Sensors/DHT22Sensor.cpp
#include "DHT22Sensor.h"

DHT22Sensor::DHT22Sensor(int sensorPin, String sensorName) 
    : pin(sensorPin), name(sensorName), available(false),
      lastTemperature(0.0), lastHumidity(0.0), lastReadTime(0) {
    dht = new DHT(pin, DHT22);
}

DHT22Sensor::~DHT22Sensor() {
    delete dht;
}

void DHT22Sensor::begin() {
    dht->begin();
    available = true;
    lastReadTime = millis();
}

float DHT22Sensor::read() {
    if (!available) return -1.0;
    readSensor();
    return lastHumidity;
}

float DHT22Sensor::readTemperature() {
    if (!available) return -273.15; // Абсолютный ноль как ошибка
    readSensor();
    return lastTemperature;
}

bool DHT22Sensor::isAvailable() {
    return available;
}

bool DHT22Sensor::isAvailable() const {
    return available;
}

String DHT22Sensor::getName() {
    return name;
}

String DHT22Sensor::getStatus() {
    String status = "DHT22[pin:" + String(pin) + 
                   ", temp:" + String(lastTemperature, 1) + "C" +
                   ", hum:" + String(lastHumidity, 1) + "%" +
                   ", available:" + String(available) + "]";
    return status;
}

bool DHT22Sensor::readSensor() {
    if (millis() - lastReadTime < 2000) return true; // Не чаще 2 сек
    
    float humidity = dht->readHumidity();
    float temperature = dht->readTemperature();
    
    if (isnan(humidity) || isnan(temperature)) {
        available = false;
        return false;
    }
    
    lastHumidity = humidity;
    lastTemperature = temperature;
    lastReadTime = millis();
    available = true;
    
    return true;
}
