// firmware/src/System/SettingsManager.cpp
#include "SettingsManager.h"

SettingsManager::SettingsManager() : settingsLoaded(false) {
    memset(&settings, 0, sizeof(settings));
    loadBuiltinDefaults();
    // Значения по умолчанию
    resetToDefaults();
}

bool SettingsManager::begin() {
    EEPROM.begin(EEPROM_SIZE);
    loadBuiltinDefaults();
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
        saveSettings();
        return false;
    }
}

bool SettingsManager::saveSettings() {
    settings.crc = calculateCRC();
    EEPROM.put(SETTINGS_ADDRESS, settings);
    return EEPROM.commit();
}

void SettingsManager::resetToDefaults() {
    // Пользовательских сетей по умолчанию нет (используем встроенные значения)
    memset(&settings, 0, sizeof(settings));
    settings.wifiCount = 0;

    // Базовый URL сервера (без дублирования /api в путях)
    // Адрес сервера больше не хранится в EEPROM
    if (defaultServerURL.length() > 0) {
        strncpy(settings.serverURL, defaultServerURL.c_str(), sizeof(settings.serverURL) - 1);
    }
     
    // Генерация DeviceID из MAC-адреса
    if (defaultMqttHost.length() > 0) {
        strncpy(settings.mqttHost, defaultMqttHost.c_str(), sizeof(settings.mqttHost) - 1);
    }
    settings.mqttPort = defaultMqttPort != 0 ? defaultMqttPort : 1883;
    if (defaultMqttUser.length() > 0) {
        strncpy(settings.mqttUser, defaultMqttUser.c_str(), sizeof(settings.mqttUser) - 1);
    }
    if (defaultMqttPass.length() > 0) {
        strncpy(settings.mqttPass, defaultMqttPass.c_str(), sizeof(settings.mqttPass) - 1);
    }

    String deviceId = defaultDeviceID.length() > 0 ? defaultDeviceID : generateDeviceIDFromMAC();
    if (deviceId.length() == 0) {
        deviceId = generateDeviceIDFromMAC();
    }
    strncpy(settings.deviceID, deviceId.c_str(), sizeof(settings.deviceID) - 1);

    settings.soilDryValue = 4095;
    settings.soilWetValue = 1800;
    settings.wateringThreshold = 30.0;
    settings.pumpMaxRunTime = 300000; // 5 минут
    
    settings.crc = calculateCRC();
    settingsLoaded = true;
}

String SettingsManager::generateDeviceIDFromMAC() {
    uint8_t mac[6];
    esp_efuse_mac_get_default(mac);
    
    String deviceID = "grovika_";
    // Берем последние 3 байта MAC-адреса
    for (int i = 3; i < 6; i++) {
        if (mac[i] < 0x10) {
            deviceID += "0";  // добавляем ведущий ноль
        }
        deviceID += String(mac[i], HEX);
    }
    deviceID.toUpperCase();  // делаем буквы заглавными
    
    return deviceID;
}

// Getters implementation
String SettingsManager::getSSID() { 
    if (settings.wifiCount > 0) return String(settings.wifi[0].ssid); 
    if (defaultWifiCount > 0) return String(defaultWifi[0].ssid);
    return String(""); 
}
String SettingsManager::getPassword() { 
    if (settings.wifiCount > 0) return String(settings.wifi[0].password); 
    if (defaultWifiCount > 0) return String(defaultWifi[0].password);
    return String(""); 
}
int SettingsManager::getWiFiCount() { 
    return static_cast<int>(settings.wifiCount) + static_cast<int>(defaultWifiCount); 
}
bool SettingsManager::getWiFiCredential(int index, String& ssid, String& password) {
    if (index < 0) {
        return false;
    }
    if (index < static_cast<int>(settings.wifiCount)) {
        ssid = String(settings.wifi[index].ssid);
        password = String(settings.wifi[index].password);
        return true;
    }
    int defaultIndex = index - static_cast<int>(settings.wifiCount);
    if (defaultIndex < 0 || defaultIndex >= static_cast<int>(defaultWifiCount)) {
        return false;
    }
    ssid = String(defaultWifi[defaultIndex].ssid);
    password = String(defaultWifi[defaultIndex].password);
    return true;
}
String SettingsManager::getServerURL() { 
    return defaultServerURL; 
}

String SettingsManager::getServerCAPem() {
    return defaultServerCAPem;
}
String SettingsManager::getDeviceID() {
    if (settings.deviceID[0] == '\0') {
        String fallback = defaultDeviceID.length() > 0 ? defaultDeviceID : generateDeviceIDFromMAC();
        if (fallback.length() == 0) {
            fallback = generateDeviceIDFromMAC();
        }
        strncpy(settings.deviceID, fallback.c_str(), sizeof(settings.deviceID) - 1);
        saveSettings();
        return fallback;
    }
    return String(settings.deviceID);
}

String SettingsManager::getMqttHost() {
    return getStringOrDefault(settings.mqttHost, defaultMqttHost);
}

uint16_t SettingsManager::getMqttPort() {
    return settings.mqttPort != 0 ? settings.mqttPort : defaultMqttPort;
}

String SettingsManager::getMqttUser() {
    return getStringOrDefault(settings.mqttUser, defaultMqttUser);
}

String SettingsManager::getMqttPass() {
    return getStringOrDefault(settings.mqttPass, defaultMqttPass);
}
int SettingsManager::getSoilDryValue() { return settings.soilDryValue; }
int SettingsManager::getSoilWetValue() { return settings.soilWetValue; }
float SettingsManager::getWateringThreshold() { return settings.wateringThreshold; }
unsigned long SettingsManager::getPumpMaxRunTime() { return settings.pumpMaxRunTime; }

// Setters implementation
void SettingsManager::setWiFiCredentials(const String& ssid, const String& password) {
    // replace index 0 for backward compatibility
    memset(&settings.wifi[0], 0, sizeof(settings.wifi[0]));
    strncpy(settings.wifi[0].ssid, ssid.c_str(), sizeof(settings.wifi[0].ssid)-1);
    strncpy(settings.wifi[0].password, password.c_str(), sizeof(settings.wifi[0].password)-1);
    if (settings.wifiCount == 0) settings.wifiCount = 1;
}

bool SettingsManager::addWiFiCredential(const String& ssid, const String& password) {
    if (ssid.length() == 0) return false;
    if (settings.wifiCount >= 10) return false;
    uint8_t idx = settings.wifiCount;
    memset(&settings.wifi[idx], 0, sizeof(settings.wifi[idx]));
    strncpy(settings.wifi[idx].ssid, ssid.c_str(), sizeof(settings.wifi[idx].ssid)-1);
    strncpy(settings.wifi[idx].password, password.c_str(), sizeof(settings.wifi[idx].password)-1);
    settings.wifiCount++;
    return true;
}

void SettingsManager::clearWiFiCredentials() {
    memset(settings.wifi, 0, sizeof(settings.wifi));
    settings.wifiCount = 0;
}

void SettingsManager::setServerConfig(const String& url, const String& id) {
    // URL игнорируется, сервер задаётся в коде. Сохраняем только deviceID.
    (void)url;
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

void SettingsManager::loadBuiltinDefaults() {
    const DefaultNetworkProfile& defaults = BUILTIN_NETWORK_DEFAULTS;
    memset(defaultWifi, 0, sizeof(defaultWifi));
    defaultWifiCount = 0;
    uint8_t limit = defaults.wifiCount;
    if (limit > DefaultNetworkProfile::MAX_WIFI) {
        limit = DefaultNetworkProfile::MAX_WIFI;
    }
    for (uint8_t i = 0; i < limit; ++i) {
        const DefaultAccessPoint& entry = defaults.wifi[i];
        if (!entry.ssid || entry.ssid[0] == '\0') {
            continue;
        }
        memset(&defaultWifi[defaultWifiCount], 0, sizeof(WiFiCredential));
        strncpy(defaultWifi[defaultWifiCount].ssid, entry.ssid, sizeof(defaultWifi[defaultWifiCount].ssid) - 1);
        if (entry.password) {
            strncpy(defaultWifi[defaultWifiCount].password, entry.password, sizeof(defaultWifi[defaultWifiCount].password) - 1);
        }
        defaultWifiCount++;
    }
    defaultServerURL = String(defaults.serverURL ? defaults.serverURL : "");
    defaultServerCAPem = String(defaults.serverCAPem ? defaults.serverCAPem : "");
    defaultMqttHost = String("growerhub.ru");
    defaultMqttPort = 1883;
    defaultMqttUser = String("mosquitto-admin");
    defaultMqttPass = String("qazwsxedc");
    defaultDeviceID = generateDeviceIDFromMAC();    
}

String SettingsManager::getStringOrDefault(const char* value, const String& fallback) {
    if (value && value[0] != '\0') {
        return String(value);
    }
    return fallback;
}
