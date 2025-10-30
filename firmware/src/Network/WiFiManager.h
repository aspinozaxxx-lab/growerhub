// firmware/src/Network/WiFiManager.h
#pragma once
#include <WiFi.h>
#include <WiFiMulti.h>
#include <Arduino.h>

class WiFiManager {
private:
    String hostname;
    bool connected;
    unsigned long lastConnectAttempt;
    const unsigned long RECONNECT_INTERVAL = 30000; // 30 секунд
    WiFiMulti wifiMulti;
    
    // Доп. логика асинхронного сканирования каждые 20 секунд
    static const unsigned long SCAN_INTERVAL = 20000; // 20 секунд
    bool scanning = false;
    unsigned long lastScanTime = 0;

    struct KnownAP { String ssid; String password; };
    KnownAP knownAPs[10];
    int knownCount = 0;
    
public:
    WiFiManager();
    
    void begin(const String& deviceHostname = "smart-watering");
    void addAccessPoint(const String& ssid, const String& password);
    void update();
    
    bool isConnected();
    String getStatus();
    String getIPAddress();
    String getSSID();
    
    void reconnect();
    
private:
    void onConnected();
    void onDisconnected();
    void startAsyncScan();
    void handleScanComplete();
};
