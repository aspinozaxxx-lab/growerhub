// firmware/src/Network/OTAUpdater.h
#pragma once
#include <ArduinoOTA.h>
#include <Arduino.h>
#include <functional>

class OTAUpdater {
private:
    String hostname;
    bool enabled;
    unsigned long lastUpdateCheck;
    
public:
    OTAUpdater();
    
    void begin(const String& deviceHostname);
    void update();
    void enable();
    void disable();
    
    String getStatus();

    // Ustanavlivaet kolbek, kotoryj budet publikovat JSON v MQTT ACK topik.
    void setAckPublisher(std::function<void(const String&)> publisher);
    
private:
    void setupOTA();
    void onStart();
    void onEnd();
    void onProgress(unsigned int progress, unsigned int total);
    void onError(ota_error_t error);

    void publishAck(const String& payload);

    std::function<void(const String&)> ackPublisher;
    int lastPublishedProgress;
};
