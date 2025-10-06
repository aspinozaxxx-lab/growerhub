// firmware/src/Network/WiFiManager.h
#pragma once
#include <WiFi.h>
#include <Arduino.h>

class WiFiManager {
private:
    String ssid;
    String password;
    String hostname;
    bool connected;
    unsigned long lastConnectAttempt;
    const unsigned long RECONNECT_INTERVAL = 30000; // 30 секунд
    
public:
    WiFiManager();
    
    void begin(const String& wifiSSID, const String& wifiPassword, 
               const String& deviceHostname = "smart-watering");
    void update();
    
    bool isConnected();
    String getStatus();
    String getIPAddress();
    String getSSID();
    
    void reconnect();
    
private:
    void onConnected();
    void onDisconnected();
};