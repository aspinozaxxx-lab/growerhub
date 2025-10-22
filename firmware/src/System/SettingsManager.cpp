// firmware/src/System/SettingsManager.cpp
#include "SettingsManager.h"
#include <FS.h>
#include "SPIFFS.h"

SettingsManager::SettingsManager() : settingsLoaded(false) {
    // Значения по умолчанию
    resetToDefaults();
}

bool SettingsManager::begin() {
    EEPROM.begin(EEPROM_SIZE);
    // Загружаем дефолты из config.ini (SPIFFS), при ошибке оставим встроенные
    if (!SPIFFS.begin(true)) {
        Serial.println("SPIFFS mount failed, using built-in defaults");
    } else {
        loadDefaultsFromConfig();
    }
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
    // Пользовательских сетей по умолчанию нет (используем дефолты из config.ini)
    memset(settings.wifi, 0, sizeof(settings.wifi));
    settings.wifiCount = 0;

    // Базовый URL сервера (без дублирования /api в путях)
    // Адрес сервера больше не хранится в EEPROM
    settings.serverURL[0] = '\0';
     
    // Генерация DeviceID из MAC-адреса
    String generatedDeviceID = generateDeviceIDFromMAC();
    strncpy(settings.deviceID, generatedDeviceID.c_str(), sizeof(settings.deviceID)-1);

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
    
    String deviceID = "esp32_";
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
    return (settings.wifiCount > 0) ? (int)settings.wifiCount : (int)defaultWifiCount; 
}
bool SettingsManager::getWiFiCredential(int index, String& ssid, String& password) {
    if (settings.wifiCount > 0) {
        if (index < 0 || index >= (int)settings.wifiCount) return false;
        ssid = String(settings.wifi[index].ssid);
        password = String(settings.wifi[index].password);
        return true;
    } else {
        if (index < 0 || index >= (int)defaultWifiCount) return false;
        ssid = String(defaultWifi[index].ssid);
        password = String(defaultWifi[index].password);
        return true;
    }
}
String SettingsManager::getServerURL() { 
    return defaultServerURL; 
}

String SettingsManager::getServerCAPem() {
    return defaultServerCAPem;
}
String SettingsManager::getDeviceID() { return String(settings.deviceID); }
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

void SettingsManager::loadDefaultsFromConfig() {
    defaultWifiCount = 0;
    defaultServerURL = String("https://growerhub.ru");
    defaultServerCAPem = String("");
    File f = SPIFFS.open("/config.ini", "r");
    if (!f) {
        Serial.println("config.ini not found, using built-in defaults");
        // Встроенные дефолты как фолбэк
        strncpy(defaultWifi[0].ssid, "JR", sizeof(defaultWifi[0].ssid)-1);
        strncpy(defaultWifi[0].password, "qazwsxedc", sizeof(defaultWifi[0].password)-1);
        strncpy(defaultWifi[1].ssid, "AKADO-E84E", sizeof(defaultWifi[1].ssid)-1);
        strncpy(defaultWifi[1].password, "90838985", sizeof(defaultWifi[1].password)-1);
        strncpy(defaultWifi[2].ssid, "TP-LINK_446C", sizeof(defaultWifi[2].ssid)-1);
        strncpy(defaultWifi[2].password, "70863765", sizeof(defaultWifi[2].password)-1);
        defaultWifiCount = 3;
        // Встроенный сертификат Let’s Encrypt E7 как фолбэк
        defaultServerCAPem = String(
            "-----BEGIN CERTIFICATE-----\n"
            "MIIEVzCCAj+gAwIBAgIRAKp18eYrjwoiCWbTi7/UuqEwDQYJKoZIhvcNAQELBQAw\n"
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n"
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjQwMzEzMDAwMDAw\n"
            "WhcNMjcwMzEyMjM1OTU5WjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n"
            "RW5jcnlwdDELMAkGA1UEAxMCRTcwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARB6AST\n"
            "CFh/vjcwDMCgQer+VtqEkz7JANurZxLP+U9TCeioL6sp5Z8VRvRbYk4P1INBmbef\n"
            "QHJFHCxcSjKmwtvGBWpl/9ra8HW0QDsUaJW2qOJqceJ0ZVFT3hbUHifBM/2jgfgw\n"
            "gfUwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcD\n"
            "ATASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSuSJ7chx1EoG/aouVgdAR4\n"
            "wpwAgDAfBgNVHSMEGDAWgBR5tFnme7bl5AFzgAiIyBpY9umbbjAyBggrBgEFBQcB\n"
            "AQQmMCQwIgYIKwYBBQUHMAKGFmh0dHA6Ly94MS5pLmxlbmNyLm9yZy8wEwYDVR0g\n"
            "BAwwCjAIBgZngQwBAgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3gxLmMubGVu\n"
            "Y3Iub3JnLzANBgkqhkiG9w0BAQsFAAOCAgEAjx66fDdLk5ywFn3CzA1w1qfylHUD\n"
            "aEf0QZpXcJseddJGSfbUUOvbNR9N/QQ16K1lXl4VFyhmGXDT5Kdfcr0RvIIVrNxF\n"
            "h4lqHtRRCP6RBRstqbZ2zURgqakn/Xip0iaQL0IdfHBZr396FgknniRYFckKORPG\n"
            "yM3QKnd66gtMst8I5nkRQlAg/Jb+Gc3egIvuGKWboE1G89NTsN9LTDD3PLj0dUMr\n"
            "OIuqVjLB8pEC6yk9enrlrqjXQgkLEYhXzq7dLafv5Vkig6Gl0nuuqjqfp0Q1bi1o\n"
            "yVNAlXe6aUXw92CcghC9bNsKEO1+M52YY5+ofIXlS/SEQbvVYYBLZ5yeiglV6t3S\n"
            "M6H+vTG0aP9YHzLn/KVOHzGQfXDP7qM5tkf+7diZe7o2fw6O7IvN6fsQXEQQj8TJ\n"
            "UXJxv2/uJhcuy/tSDgXwHM8Uk34WNbRT7zGTGkQRX0gsbjAea/jYAoWv0ZvQRwpq\n"
            "Pe79D/i7Cep8qWnA+7AE/3B3S/3dEEYmc0lpe1366A/6GEgk3ktr9PEoQrLChs6I\n"
            "tu3wnNLB2euC8IKGLQFpGtOO/2/hiAKjyajaBP25w1jF0Wl8Bbqne3uZ2q1GyPFJ\n"
            "YRmT7/OXpmOH/FVLtwS+8ng1cAmpCujPwteJZNcDG0sF2n/sc0+SQf49fdyUK0ty\n"
            "+VUwFj9tmWxyR/M=\n"
            "-----END CERTIFICATE-----\n"
        );
        return;
    }
    String section = "";
    bool inCert = false;
    while (f.available()) {
        String line = f.readStringUntil('\n');
        line.trim();
        if (line.length() == 0) continue;
        if (line[0] == ';' || line[0] == '#') continue;
        if (inCert) {
            if (line.equalsIgnoreCase("ca_pem_end")) {
                inCert = false;
                continue;
            }
            // накапливаем PEM как есть
            defaultServerCAPem += line;
            defaultServerCAPem += '\n';
            continue;
        }
        if (line.startsWith("[") && line.endsWith("]")) {
            section = line.substring(1, line.length()-1);
            section.toLowerCase();
            continue;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
            // поддержка блоков без '=' внутри [tls]
            if (section == "tls" && line.equalsIgnoreCase("ca_pem_begin")) {
                defaultServerCAPem = String("");
                inCert = true;
            }
            continue;
        }
        String key = line.substring(0, eq); key.trim(); key.toLowerCase();
        String val = line.substring(eq+1); val.trim();
        if (section == "server") {
            if (key == "base_url") {
                defaultServerURL = val;
            }
        } else if (section == "wifi") {
            if (key == "ap") {
                int colon = val.indexOf(':');
                if (colon > 0 && defaultWifiCount < 10) {
                    String s = val.substring(0, colon);
                    String p = val.substring(colon+1);
                    memset(&defaultWifi[defaultWifiCount], 0, sizeof(defaultWifi[defaultWifiCount]));
                    strncpy(defaultWifi[defaultWifiCount].ssid, s.c_str(), sizeof(defaultWifi[defaultWifiCount].ssid)-1);
                    strncpy(defaultWifi[defaultWifiCount].password, p.c_str(), sizeof(defaultWifi[defaultWifiCount].password)-1);
                    defaultWifiCount++;
                }
            }
        } else if (section == "tls") {
            if (key == "ca_pem" && val.length() > 0) {
                // однострочный вариант (не рекомендуется для PEM)
                defaultServerCAPem = val;
            }
        }
    }
    f.close();
    Serial.println("Loaded defaults from config.ini: wifiCount=" + String(defaultWifiCount) + ", server=" + defaultServerURL);
    if (defaultServerCAPem.length() == 0) {
        Serial.println("No CA PEM in config.ini; using built-in fallback");
    }
}
