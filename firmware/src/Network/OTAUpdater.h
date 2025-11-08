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
    // Ustanavlivaet PEM sertifikat servera dlya HTTPS OTA.
    void setServerCert(const String& certPem);
    // Pozvolyaet izvne opublikovat gotovy ACK payload.
    void publishImmediateAck(const String& payload);

    // Startuet pull-OTA po HTTPS; vozvrashaet true esli nachato uspeshno.
    bool beginPull(const String& url, const String& version, const String& sha256Hex);
    
private:
    void setupOTA();
    void onStart();
    void onEnd();
    void onProgress(unsigned int progress, unsigned int total);
    void onError(ota_error_t error);

    void publishAck(const String& payload);
    void publishProgressPercent(int percent);

    std::function<void(const String&)> ackPublisher;
    int lastPublishedProgress;
    String serverCertPem;
};
