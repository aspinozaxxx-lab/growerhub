// firmware/src/Network/HTTPClient.h
#pragma once
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Arduino.h>

class WateringHTTPClient {
private:
    String serverURL;
    String deviceID;
    String caPem;
    bool enabled;
    unsigned long lastSendTime;
    const unsigned long SEND_INTERVAL = 60000; // 1 минута
    
public:
    WateringHTTPClient();
    
    void begin(const String& serverBaseURL, const String& id, const String& caPemPEM);
    void update(float soilMoisture, float airTemperature, float airHumidity, 
                bool pumpState, bool lightState);
    
    void enable();
    void disable();
    String getStatus();
    
    bool sendSensorData(float soilMoisture, float airTemperature, float airHumidity,
                       bool isWatering, bool isLightOn);
    bool sendActuatorState(bool pumpState, bool lightState);
    bool sendSystemStatus(const String& status);

    bool discoverEndpoints();
    
private:
    bool sendData(const String& endpoint, const JsonDocument& doc);
    String getTimestamp();
};
