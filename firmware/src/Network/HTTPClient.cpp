// firmware/src/Network/HTTPClient.cpp
#include "HTTPClient.h"
#include <WiFiClientSecure.h>
#include <WiFi.h>
#include "System/SystemClock.h"

WateringHTTPClient::WateringHTTPClient() 
    : enabled(false), lastSendTime(0), timeProvider(nullptr) {}

std::function<bool()> WateringHTTPClient::wifiOnlineProvider = []() {
    return WiFi.status() == WL_CONNECTED;
};

void WateringHTTPClient::begin(const String& serverBaseURL, const String& id, const String& caPemPEM) {
    serverURL = serverBaseURL;
    deviceID = id;
    caPem = caPemPEM;
    enable();
}

void WateringHTTPClient::update(float soilMoisture, float airTemperature, float airHumidity, 
                               bool pumpState, bool lightState) {
    if (!enabled) return;
    
    if (millis() - lastSendTime >= SEND_INTERVAL) {
        // Передаем все 5 параметров
        sendSensorData(soilMoisture, airTemperature, airHumidity, pumpState, lightState);
        lastSendTime = millis();
    }
}

void WateringHTTPClient::enable() {
    enabled = true;
}

void WateringHTTPClient::disable() {
    enabled = false;
}

void WateringHTTPClient::setTimeProvider(SystemClock* clock) {
    timeProvider = clock;
}

String WateringHTTPClient::getStatus() {
    return "HTTPClient[Enabled:" + String(enabled ? "Yes" : "No") + 
           ", Server:" + serverURL + "]";
}

// Добавляем в WateringHTTPClient.cpp
bool WateringHTTPClient::discoverEndpoints() {
    if (!wifiOnlineProvider || !wifiOnlineProvider()) return false;
    
    WiFiClientSecure client;
    if (caPem.length() > 0) client.setCACert(caPem.c_str());
    HTTPClient http;
    http.begin(client, serverURL + "/");
    int httpCode = http.GET();
    
    if (httpCode == 200) {
        String response = http.getString();
        Serial.println("Endpoint discovery: " + response);
        // Здесь можно парсить ответ чтобы найти доступные эндпоинты
    }
    
    http.end();
    return httpCode == 200;
}

String WateringHTTPClient::getTimestamp() {
    if (timeProvider) {
        time_t utcNow = 0;
        if (timeProvider->nowUtc(utcNow)) {
            return timeProvider->formatIso8601(utcNow);
        }
    }

    // Временная реализация - используем миллисы
    // В будущем можно добавить NTP синхронизацию
    unsigned long now = millis();
    
    unsigned long seconds = now / 1000;
    unsigned long minutes = seconds / 60;
    unsigned long hours = minutes / 60;
    unsigned long days = hours / 24;
    
    seconds %= 60;
    minutes %= 60;
    hours %= 24;
    
    char timestamp[20];
    snprintf(timestamp, sizeof(timestamp), "%lud %02lu:%02lu:%02lu", 
             days, hours, minutes, seconds);
    
    return String(timestamp);
}

bool WateringHTTPClient::sendSensorData(float soilMoisture, float airTemperature, float airHumidity, 
                                       bool isWatering, bool isLightOn) {
    StaticJsonDocument<512> doc;
    
    doc["device_id"] = deviceID;
    doc["soil_moisture"] = soilMoisture;
    doc["air_temperature"] = airTemperature; 
    doc["air_humidity"] = airHumidity;
    doc["is_watering"] = isWatering;    // Реальное состояние
    doc["is_light_on"] = isLightOn;     // Реальное состояние
    doc["timestamp"] = getTimestamp();
    
    String endpoint = "/api/device/" + deviceID + "/status";
    return sendData(endpoint, doc);
}

bool WateringHTTPClient::sendActuatorState(bool pumpState, bool lightState) {
    // На твоем сервере нет отдельного эндпоинта для актуаторов
    // Отправляем вместе с сенсорами или логируем отдельно
    Serial.println("Actuators - Pump: " + String(pumpState ? "ON" : "OFF") + 
                  ", Light: " + String(lightState ? "ON" : "OFF"));
    return true;
}

bool WateringHTTPClient::sendSystemStatus(const String& status) {
    // Используем тот же эндпоинт что и для сенсоров
    StaticJsonDocument<256> doc;
    doc["system_status"] = status;
    doc["free_heap"] = ESP.getFreeHeap();
    doc["uptime"] = millis();
    doc["timestamp"] = getTimestamp();
    
    String endpoint = "/api/device/" + deviceID + "/status";
    return sendData(endpoint, doc);
}

bool WateringHTTPClient::sendData(const String& endpoint, const JsonDocument& doc) {
    if (!wifiOnlineProvider || !wifiOnlineProvider()) {
        Serial.println("HTTP: No WiFi connection");
        return false;
    }
    
    WiFiClientSecure client;
    if (caPem.length() > 0) client.setCACert(caPem.c_str());
    HTTPClient http;
    
    // Добавляем логирование URL
    String fullURL = serverURL + endpoint;
    Serial.println("HTTP: Sending to " + fullURL);
    
    http.begin(client, fullURL);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(5000);
    
    String jsonString;
    serializeJson(doc, jsonString);
    
    Serial.println("HTTP: Payload: " + jsonString);
    
    int httpCode = http.POST(jsonString);
    bool success = (httpCode == 200 || httpCode == 201);
    
    if (success) {
        Serial.println("HTTP: Success, code: " + String(httpCode));
    } else {
        Serial.println("HTTP: Error, code: " + String(httpCode));
        // Выводим ответ сервера если есть
        if (httpCode > 0) {
            String response = http.getString();
            Serial.println("HTTP: Response: " + response);
        }
    }
    
    http.end();
    return success;
}

void WateringHTTPClient::setWiFiOnlineProvider(std::function<bool()> provider) {
    // Позволяет переиспользовать флаг онлайн-статуса из WiFiService.
    if (provider) {
        wifiOnlineProvider = std::move(provider);
    } else {
        wifiOnlineProvider = []() { return WiFi.status() == WL_CONNECTED; };
    }
}
