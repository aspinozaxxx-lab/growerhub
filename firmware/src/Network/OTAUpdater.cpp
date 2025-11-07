// firmware/src/Network/OTAUpdater.cpp
#include "OTAUpdater.h"
#include <utility>

#ifndef OTA_PASSWORD
#define OTA_PASSWORD "growerhub-ota"
#endif

extern const char* FW_VERSION;

OTAUpdater::OTAUpdater()
    : enabled(false),
      lastUpdateCheck(0),
      ackPublisher(nullptr),
      lastPublishedProgress(-1) {}

void OTAUpdater::begin(const String& deviceHostname) {
    hostname = deviceHostname;
    lastPublishedProgress = -1;
    setupOTA();
    enable();
}

void OTAUpdater::update() {
    if (enabled) {
        ArduinoOTA.handle();
    }
}

void OTAUpdater::enable() {
    enabled = true;
}

void OTAUpdater::disable() {
    enabled = false;
}

String OTAUpdater::getStatus() {
    return "OTA[Enabled:" + String(enabled ? "Yes" : "No") + 
           ", Hostname:" + hostname + "]";
}

void OTAUpdater::setAckPublisher(std::function<void(const String&)> publisher) {
    ackPublisher = std::move(publisher);
}

void OTAUpdater::setupOTA() {
    ArduinoOTA.setHostname(hostname.c_str());
    ArduinoOTA.setPassword(OTA_PASSWORD);
    Serial.println(F("OTA: parol ustanovlen (pereopredeli cherez OTA_PASSWORD)."));
    
    ArduinoOTA.onStart([this]() { this->onStart(); });
    ArduinoOTA.onEnd([this]() { this->onEnd(); });
    ArduinoOTA.onProgress([this](unsigned int progress, unsigned int total) { 
        this->onProgress(progress, total); 
    });
    ArduinoOTA.onError([this](ota_error_t error) { 
        this->onError(error); 
    });
    
    ArduinoOTA.begin();
}

void OTAUpdater::onStart() {
    Serial.println("OTA Update Started");
    lastPublishedProgress = -1;
    publishAck(String("{\"type\":\"ota\",\"result\":\"accepted\",\"status\":\"running\",\"message\":\"start\"}"));
}

void OTAUpdater::onEnd() {
    Serial.println("\nOTA Update Finished");
    if (FW_VERSION) {
        String payload = String("{\"type\":\"ota\",\"result\":\"accepted\",\"status\":\"done\",\"fw\":\"") +
                         String(FW_VERSION) + "\"}";
        publishAck(payload);
    } else {
        publishAck(String("{\"type\":\"ota\",\"result\":\"accepted\",\"status\":\"done\"}"));
    }
}

void OTAUpdater::onProgress(unsigned int progress, unsigned int total) {
    const int percent = (total == 0) ? 0 : static_cast<int>((progress * 100UL) / total);
    Serial.printf("OTA progress: %d%%\r", percent);
    if (total == 0) {
        return;
    }
    if (lastPublishedProgress >= 0 && percent - lastPublishedProgress < 5 && percent != 100) {
        return;
    }
    lastPublishedProgress = percent;
    String payload = String("{\"type\":\"ota\",\"result\":\"accepted\",\"status\":\"running\",\"progress\":") +
                     String(percent) + "}";
    publishAck(payload);
}

void OTAUpdater::onError(ota_error_t error) {
    Serial.printf("OTA Error[%u]: ", error);
    switch (error) {
        case OTA_AUTH_ERROR: Serial.println("Auth Failed"); break;
        case OTA_BEGIN_ERROR: Serial.println("Begin Failed"); break;
        case OTA_CONNECT_ERROR: Serial.println("Connect Failed"); break;
        case OTA_RECEIVE_ERROR: Serial.println("Receive Failed"); break;
        case OTA_END_ERROR: Serial.println("End Failed"); break;
        default: Serial.println("Unknown Error"); break;
    }
    String reason;
    switch (error) {
        case OTA_AUTH_ERROR: reason = "auth"; break;
        case OTA_BEGIN_ERROR: reason = "begin"; break;
        case OTA_CONNECT_ERROR: reason = "connect"; break;
        case OTA_RECEIVE_ERROR: reason = "receive"; break;
        case OTA_END_ERROR: reason = "end"; break;
        default: reason = "unknown"; break;
    }
    String payload = String("{\"type\":\"ota\",\"result\":\"rejected\",\"status\":\"error\",\"error\":\"") +
                     reason + "\"}";
    publishAck(payload);
}

void OTAUpdater::publishAck(const String& payload) {
    if (!ackPublisher) {
        return;
    }
    ackPublisher(payload);
}
