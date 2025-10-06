// firmware/src/System/SettingsManager.h
#pragma once
#include <EEPROM.h>
#include <Arduino.h>

struct SystemSettings {
    char ssid[32];
    char password[32];
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
    const int EEPROM_SIZE = 512;
    const int SETTINGS_ADDRESS = 0;
    
public:
    SettingsManager();
    
    bool begin();
    bool loadSettings();
    bool saveSettings();
    void resetToDefaults();
    
    // Getters
    String getSSID();
    String getPassword();
    String getServerURL();
    String getDeviceID();
    int getSoilDryValue();
    int getSoilWetValue();
    float getWateringThreshold();
    unsigned long getPumpMaxRunTime();
    
    // Setters
    void setWiFiCredentials(const String& ssid, const String& password);
    void setServerConfig(const String& url, const String& id);
    void setSoilCalibration(int dry, int wet);
    void setWateringThreshold(float threshold);
    void setPumpMaxRunTime(unsigned long runTime);
    
    String getStatus();
    
private:
    uint32_t calculateCRC();
    bool validateCRC();
};