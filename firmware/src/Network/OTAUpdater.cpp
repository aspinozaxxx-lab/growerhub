// firmware/src/Network/OTAUpdater.cpp
#include "OTAUpdater.h"
#include <utility>
#include <esp_https_ota.h>
#include <esp_http_client.h>
#include <esp_ota_ops.h>

#ifndef OTA_PASSWORD
#define OTA_PASSWORD "growerhub-ota"
#endif

extern const char* FW_VERSION;

OTAUpdater::OTAUpdater()
    : enabled(false),
      lastUpdateCheck(0),
      ackPublisher(nullptr),
      lastPublishedProgress(-1),
      serverCertPem("") {}

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

void OTAUpdater::setServerCert(const String& certPem) {
    serverCertPem = certPem;
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
    publishProgressPercent(percent);
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

void OTAUpdater::publishProgressPercent(int percent) {
    if (percent < 0 || percent > 100) {
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

bool OTAUpdater::beginPull(const String& url, const String& version, const String& sha256Hex) {
    if (url.length() == 0) {
        Serial.println(F("OTA(PULL): pustoj URL, komandу otklonyaem."));
        publishAck(String("{\"type\":\"ota\",\"result\":\"rejected\",\"status\":\"error\",\"error\":\"bad_url\"}"));
        return false;
    }

    Serial.print(F("OTA(PULL): start url="));
    Serial.println(url);
    Serial.println(F("OTA(PULL): start."));
    lastPublishedProgress = -1;
    publishAck(String("{\"type\":\"ota\",\"result\":\"accepted\",\"status\":\"running\",\"message\":\"start\"}"));
    publishProgressPercent(5);

    if (sha256Hex.length() > 0) {
        Serial.println(F("OTA(PULL): poluchen sha256, TODO: verify sha256.")); // TODO: verify sha256
    }

    esp_http_client_config_t httpConfig = {};
    httpConfig.url = url.c_str();
    httpConfig.timeout_ms = 15000;
    httpConfig.keep_alive_enable = true;
    httpConfig.disable_auto_redirect = false;
    httpConfig.skip_cert_common_name_check = false;
    if (serverCertPem.length() > 0) {
        httpConfig.cert_pem = serverCertPem.c_str();
    }

    esp_https_ota_config_t otaConfig = {};
    otaConfig.http_config = &httpConfig;

    esp_https_ota_handle_t handle = nullptr;
    esp_err_t err = esp_https_ota_begin(&otaConfig, &handle);
    if (err != ESP_OK) {
        Serial.printf("OTA(PULL): begin fail err=%d\n", err);
        publishAck(String("{\"type\":\"ota\",\"result\":\"rejected\",\"status\":\"error\",\"error\":\"begin\"}"));
        Serial.println(F("OTA(PULL): fail begin."));
        if (handle) {
            esp_https_ota_abort(handle);
        }
        return false;
    }

    publishProgressPercent(25);
    Serial.println(F("OTA(PULL): conn ok."));
    bool downloadStagePublished = false;
    while (true) {
        err = esp_https_ota_perform(handle);
        if (err == ESP_ERR_HTTPS_OTA_IN_PROGRESS) {
            if (!downloadStagePublished) {
                publishProgressPercent(50);
                Serial.println(F("OTA(PULL): download..."));
                downloadStagePublished = true;
            }
            delay(10);
            continue;
        }
        break;
    }

    if (err != ESP_OK) {
        Serial.printf("OTA(PULL): download fail err=%d\n", err);
        publishAck(String("{\"type\":\"ota\",\"result\":\"rejected\",\"status\":\"error\",\"error\":\"download\"}"));
        Serial.println(F("OTA(PULL): fail download."));
        esp_https_ota_abort(handle);
        return false;
    }

    publishProgressPercent(75);
    Serial.println(F("OTA(PULL): apply stage."));
    err = esp_https_ota_finish(handle);
    if (err != ESP_OK) {
        Serial.printf("OTA(PULL): finish fail err=%d\n", err);
        publishAck(String("{\"type\":\"ota\",\"result\":\"rejected\",\"status\":\"error\",\"error\":\"apply\"}"));
        Serial.println(F("OTA(PULL): fail apply."));
        return false;
    }

    publishProgressPercent(100);
    const String reportedVersion = version.length() > 0
        ? version
        : (FW_VERSION ? String(FW_VERSION) : String("unknown"));
    String payload = String("{\"type\":\"ota\",\"result\":\"accepted\",\"status\":\"done\",\"fw\":\"") +
                     reportedVersion + "\"}";
    publishAck(payload);
    Serial.println(F("OTA(PULL): ok, restart."));
    delay(500);
    ESP.restart();
    return true;
}
