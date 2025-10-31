// firmware/src/System/SettingsManager.cpp
#include "SettingsManager.h"

SettingsManager::SettingsManager() {}

void SettingsManager::begin() {  
    SetSettings();     
}
 void SettingsManager::SetSettings() {
        
    settings.wifiCount = BUILTIN_NETWORK_DEFAULTS.wifiCount;
    for (uint8_t i = 0; i < BUILTIN_NETWORK_DEFAULTS.wifiCount; i++) {
        settings.wifi[i] = BUILTIN_NETWORK_DEFAULTS.wifi[i];
    }          
    
    settings.mqttPort = 1883;
    settings.mqttUser = String("mosquitto-admin");
    settings.mqttPass = String("qazwsxedc");
    settings.deviceID = generateDeviceIDFromMAC();
    settings.mqttHost = String("growerhub.ru");
    settings.serverURL = String(BUILTIN_NETWORK_DEFAULTS.serverURL);
    settings.serverCAPem = String(BUILTIN_NETWORK_DEFAULTS.serverCAPem);
    
    settings.soilDryValue = 4095;
    settings.soilWetValue = 1800;
    settings.wateringThreshold = 30.0;
    settings.pumpMaxRunTime = 300000; // 5 минут
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
/* String SettingsManager::getSSID() { 
    if (settings.wifiCount > 0) return String(settings.wifi[0].ssid);  
    return String(""); 
}
String SettingsManager::getPassword() { 
    if (settings.wifiCount > 0) return String(settings.wifi[0].password); 
    return String(""); 
} */
int SettingsManager::getWiFiCount() { 
    return static_cast<int>(settings.wifiCount);
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
    return false;
}
std::size_t SettingsManager::getWiFiCount() const {
    return static_cast<std::size_t>(settings.wifiCount);
}

bool SettingsManager::getWiFiCredential(std::size_t index, const char*& ssid, const char*& password) const {
    if (index >= settings.wifiCount) {
        ssid = nullptr;
        password = nullptr;
        return false;
    }

    ssid = settings.wifi[index].ssid.c_str();
    password = settings.wifi[index].password.c_str();
    return true;
}
String SettingsManager::getServerURL() { 
    return settings.serverURL; 
}

String SettingsManager::getServerCAPem() {
    return settings.serverCAPem;    
}
String SettingsManager::getDeviceID() {    
    return settings.deviceID;
}

String SettingsManager::getMqttHost() {
    return settings.mqttHost;
}

uint16_t SettingsManager::getMqttPort() {
    return settings.mqttPort;
}

String SettingsManager::getMqttUser() {
    return settings.mqttUser;
}

String SettingsManager::getMqttPass() {
    return settings.mqttPass;
}
int SettingsManager::getSoilDryValue() { return settings.soilDryValue; }
int SettingsManager::getSoilWetValue() { return settings.soilWetValue; }
float SettingsManager::getWateringThreshold() { return settings.wateringThreshold; }
unsigned long SettingsManager::getPumpMaxRunTime() { return settings.pumpMaxRunTime; }

// Setters implementation
/* void SettingsManager::setWiFiCredentials(const String& ssid, const String& password) {
    // replace index 0 for backward compatibility
    memset(&settings.wifi[0], 0, sizeof(settings.wifi[0]));
    strncpy(settings.wifi[0].ssid, ssid.c_str(), sizeof(settings.wifi[0].ssid)-1);
    strncpy(settings.wifi[0].password, password.c_str(), sizeof(settings.wifi[0].password)-1);
    if (settings.wifiCount == 0) settings.wifiCount = 1;
} */

/* bool SettingsManager::addWiFiCredential(const String& ssid, const String& password) {
    if (ssid.length() == 0) return false;
    if (settings.wifiCount >= 10) return false;
    uint8_t idx = settings.wifiCount;
    memset(&settings.wifi[idx], 0, sizeof(settings.wifi[idx]));
    strncpy(settings.wifi[idx].ssid, ssid.c_str(), sizeof(settings.wifi[idx].ssid)-1);
    strncpy(settings.wifi[idx].password, password.c_str(), sizeof(settings.wifi[idx].password)-1);
    settings.wifiCount++;
    return true;
} */

/* void SettingsManager::clearWiFiCredentials() {
    memset(settings.wifi, 0, sizeof(settings.wifi));
    settings.wifiCount = 0;
} */

/* void SettingsManager::setServerConfig(const String& url, const String& id) {
    // URL игнорируется, сервер задаётся в коде. Сохраняем только deviceID.
    (void)url;
    strncpy(settings.deviceID, id.c_str(), sizeof(settings.deviceID)-1);
} */

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
    return "Status: Threshold:" + String(getWateringThreshold(), 1) + "%]";
}

 /* void SettingsManager::loadBuiltinDefaults() {
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
}   */
