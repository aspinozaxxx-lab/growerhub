// firmware/src/Network/OTAUpdater.cpp
#include "OTAUpdater.h"

OTAUpdater::OTAUpdater() : enabled(false), lastUpdateCheck(0) {}

void OTAUpdater::begin(const String& deviceHostname) {
    hostname = deviceHostname;
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

void OTAUpdater::setupOTA() {
    ArduinoOTA.setHostname(hostname.c_str());
    
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
}

void OTAUpdater::onEnd() {
    Serial.println("\nOTA Update Finished");
}

void OTAUpdater::onProgress(unsigned int progress, unsigned int total) {
    Serial.printf("Progress: %u%%\r", (progress / (total / 100)));
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
}