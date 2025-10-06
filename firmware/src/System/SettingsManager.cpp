// firmware/src/System/SettingsManager.cpp
#include "SettingsManager.h"

SettingsManager::SettingsManager() : settingsLoaded(false) {
    // Значения по умолчанию
    resetToDefaults();
}

bool SettingsManager::begin() {
    EEPROM.begin(EEPROM_SIZE);
    return loadSettings();
}

bool SettingsManager::loadSettings() {
    EEPROM.get(SETTINGS_ADDRESS, settings);
    
    if (validateCRC()) {
        settingsLoaded = true;
        Serial.println("Settings loaded successfully");
        return true;
    } else {
        Serial.println("Invalid settings CRC, using defaults");
        resetToDefaults();
        return false;
    }
}

bool SettingsManager::saveSettings() {
    settings.crc = calculateCRC();
    EEPROM.put(SETTINGS_ADDRESS, settings);
    return EEPROM.commit();
}

void SettingsManager::resetToDefaults() {
    strncpy(settings.ssid, "JR", sizeof(settings.ssid)-1);
    strncpy(settings.password, "qazwsxedc", sizeof(settings.password)-1);
    strncpy(settings.serverURL, "http://192.168.0.11:8000", sizeof(settings.serverURL)-1);
    strncpy(settings.deviceID, "esp32_01", sizeof(settings.deviceID)-1); // Фиксированное значение
    
    settings.soilDryValue = 4095;
    settings.soilWetValue = 1800;
    settings.wateringThreshold = 30.0;
    settings.pumpMaxRunTime = 300000; // 5 минут
    
    settings.crc = calculateCRC();
    settingsLoaded = true;
}

// Getters implementation
String SettingsManager::getSSID() { return String(settings.ssid); }
String SettingsManager::getPassword() { return String(settings.password); }
String SettingsManager::getServerURL() { return String(settings.serverURL); }
String SettingsManager::getDeviceID() { return String(settings.deviceID); }
int SettingsManager::getSoilDryValue() { return settings.soilDryValue; }
int SettingsManager::getSoilWetValue() { return settings.soilWetValue; }
float SettingsManager::getWateringThreshold() { return settings.wateringThreshold; }
unsigned long SettingsManager::getPumpMaxRunTime() { return settings.pumpMaxRunTime; }

// Setters implementation
void SettingsManager::setWiFiCredentials(const String& ssid, const String& password) {
    strncpy(settings.ssid, ssid.c_str(), sizeof(settings.ssid)-1);
    strncpy(settings.password, password.c_str(), sizeof(settings.password)-1);
}

void SettingsManager::setServerConfig(const String& url, const String& id) {
    strncpy(settings.serverURL, url.c_str(), sizeof(settings.serverURL)-1);
    strncpy(settings.deviceID, id.c_str(), sizeof(settings.deviceID)-1);
}

void SettingsManager::setSoilCalibration(int dry, int wet) {
    settings.soilDryValue = dry;
    settings.soilWetValue = wet;
}

void SettingsManager::setWateringThreshold(float threshold) {
    settings.wateringThreshold = threshold;
}

void SettingsManager::setPumpMaxRunTime(unsigned long runTime) {
    settings.pumpMaxRunTime = runTime;
}

String SettingsManager::getStatus() {
    return "Settings[Loaded:" + String(settingsLoaded ? "Yes" : "No") +
           ", SSID:" + getSSID() + 
           ", Threshold:" + String(getWateringThreshold(), 1) + "%]";
}

uint32_t SettingsManager::calculateCRC() {
    // Простая реализация CRC (в реальном проекте использовать более надежную)
    uint32_t crc = 0;
    uint8_t* data = (uint8_t*)&settings;
    size_t dataSize = sizeof(settings) - sizeof(settings.crc);
    
    for (size_t i = 0; i < dataSize; i++) {
        crc += data[i];
    }
    return crc;
}

bool SettingsManager::validateCRC() {
    return settings.crc == calculateCRC();
}