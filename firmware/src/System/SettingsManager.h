// firmware/src/System/SettingsManager.h
#pragma once
#include <EEPROM.h>
#include <Arduino.h>
#include "esp_efuse.h"

struct WiFiCredential {
    char ssid[32];
    char password[32];
};

struct SystemSettings {
    // Пользовательские сети (если заданы пользователем и сохранены)
    WiFiCredential wifi[10]; // до 10 сетей
    uint8_t wifiCount;       // текущее число пользовательских сетей
    char serverURL[64];
    char deviceID[16];
    
    int soilDryValue;
    int soilWetValue;
    float wateringThreshold;
    unsigned long pumpMaxRunTime;
    
    uint32_t crc;
};

class SettingsManager {
private:
    SystemSettings settings;
    bool settingsLoaded;
    const int EEPROM_SIZE = 1024; // увеличено из-за хранения до 10 Wi-Fi сетей
    const int SETTINGS_ADDRESS = 0;

    // Дефолтные значения из config.ini (не хранятся в EEPROM)
    WiFiCredential defaultWifi[10];
    uint8_t defaultWifiCount = 0;
    String defaultServerURL = String("https://growerhub.ru");
    
public:
    SettingsManager();
    
    bool begin();
    bool loadSettings();
    bool saveSettings();
    void resetToDefaults();
    
    // Getters
    String getSSID();      // первый SSID из пользовательских либо дефолтов
    String getPassword();  // пароль к первому SSID
    int getWiFiCount();    // число пользовательских или дефолтных сетей
    bool getWiFiCredential(int index, String& ssid, String& password);
    String getServerURL(); // теперь из config.ini (или дефолт в коде)
    String getServerCAPem(); // PEM сертификат сервера из config.ini (или дефолт)
    String getDeviceID();
    int getSoilDryValue();
    int getSoilWetValue();
    float getWateringThreshold();
    unsigned long getPumpMaxRunTime();
    
    // Setters
    void setWiFiCredentials(const String& ssid, const String& password); // заменяет [0]
    bool addWiFiCredential(const String& ssid, const String& password);  // добавляет, до 10
    void clearWiFiCredentials();
    void setServerConfig(const String& url, const String& id); // url игнорируется
    void setSoilCalibration(int dry, int wet);
    void setWateringThreshold(float threshold);
    void setPumpMaxRunTime(unsigned long runTime);
    
    String getStatus();
    
private:
    uint32_t calculateCRC();
    bool validateCRC();
    String generateDeviceIDFromMAC();
    void loadDefaultsFromConfig();
    String defaultServerCAPem;
};
