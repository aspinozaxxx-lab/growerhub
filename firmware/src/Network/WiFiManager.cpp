// firmware/src/Network/WiFiManager.cpp
#include "WiFiManager.h"

WiFiManager::WiFiManager() 
    : connected(false), lastConnectAttempt(0) {}

void WiFiManager::begin(const String& wifiSSID, const String& wifiPassword, 
                       const String& deviceHostname) {
    ssid = wifiSSID;
    password = wifiPassword;
    hostname = deviceHostname;
    
    WiFi.mode(WIFI_STA);
    WiFi.setHostname(hostname.c_str());
    
    reconnect();
}

void WiFiManager::update() {
    if (!connected && millis() - lastConnectAttempt >= RECONNECT_INTERVAL) {
        reconnect();
    }
}

bool WiFiManager::isConnected() {
    return WiFi.status() == WL_CONNECTED;
}

String WiFiManager::getStatus() {
    if (isConnected()) {
        return "WiFi[Connected, SSID:" + getSSID() + ", IP:" + getIPAddress() + "]";
    } else {
        return "WiFi[Disconnected, SSID:" + ssid + "]";
    }
}

String WiFiManager::getIPAddress() {
    return WiFi.localIP().toString();
}

String WiFiManager::getSSID() {
    return WiFi.SSID();
}

void WiFiManager::reconnect() {
    if (isConnected()) return;
    
    Serial.println("Connecting to WiFi: " + ssid);
    WiFi.begin(ssid.c_str(), password.c_str());
    lastConnectAttempt = millis();
    
    // Ждем подключения до 10 секунд
    unsigned long startTime = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - startTime < 10000) {
        delay(500);
        Serial.print(".");
    }
    
    if (isConnected()) {
        onConnected();
    } else {
        onDisconnected();
    }
}

void WiFiManager::onConnected() {
    connected = true;
    Serial.println("\nWiFi connected!");
    Serial.println("IP address: " + getIPAddress());
}

void WiFiManager::onDisconnected() {
    connected = false;
    Serial.println("\nWiFi connection failed");
}