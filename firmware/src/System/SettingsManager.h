// firmware/src/System/SettingsManager.h
#pragma once
//#include <EEPROM.h>
#include <Arduino.h>
#include "esp_efuse.h"

/* struct DefaultAccessPoint {
    const char* ssid;
    const char* password;
}; */

struct WiFiCredential {
    String ssid;
    String password;
};

/* struct WiFiCredential {
    char ssid[32];
    char password[32];
}; */
struct DefaultNetworkProfile {
    static const uint8_t MAX_WIFI = 10;
    uint8_t wifiCount;
    WiFiCredential wifi[MAX_WIFI];
    const char* serverURL;
    const char* serverCAPem;
};

struct SystemSettings {
    // Пользовательские сети (если заданы пользователем и сохранены)
    WiFiCredential wifi[10]; // до 10 сетей
    uint8_t wifiCount;       // текущее число пользовательских сетей
    //char serverURL[64];
    String serverURL;
    //har mqttHost[64];
    String mqttHost;
    uint16_t mqttPort;
    //char mqttUser[32];
    //char mqttPass[32];
    //char deviceID[16];
    String mqttUser;
    String mqttPass;
    String deviceID;
    int soilDryValue;
    int soilWetValue;
    float wateringThreshold;
    unsigned long pumpMaxRunTime;
    String serverCAPem;
    
   // uint32_t crc;
};

class SettingsManager {
private:
    SystemSettings settings;
    DefaultNetworkProfile BUILTIN_NETWORK_DEFAULTS = {
    3,
    {
        {"JR", "qazwsxedc"},
        {"AKADO-E84E", "90838985"},
        {"TP-LINK_446C", "70863765"},
        {"", ""},
        {"", ""},
        {"", ""},
        {"", ""},
        {"", ""},
        {"", ""},
        {"", ""}
    },
    "https://growerhub.ru",
    R"(-----BEGIN CERTIFICATE-----
MIIEVzCCAj+gAwIBAgIRAKp18eYrjwoiCWbTi7/UuqEwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjQwMzEzMDAwMDAw
WhcNMjcwMzEyMjM1OTU5WjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg
RW5jcnlwdDELMAkGA1UEAxMCRTcwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARB6AST
CFh/vjcwDMCgQer+VtqEkz7JANurZxLP+U9TCeioL6sp5Z8VRvRbYk4P1INBmbef
QHJFHCxcSjKmwtvGBWpl/9ra8HW0QDsUaJW2qOJqceJ0ZVFT3hbUHifBM/2jgfgw
gfUwDgYDVR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcD
ATASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBSuSJ7chx1EoG/aouVgdAR4
wpwAgDAfBgNVHSMEGDAWgBR5tFnme7bl5AFzgAiIyBpY9umbbjAyBggrBgEFBQcB
AQQmMCQwIgYIKwYBBQUHMAKGFmh0dHA6Ly94MS5pLmxlbmNyLm9yZy8wEwYDVR0g
BAwwCjAIBgZngQwBAgEwJwYDVR0fBCAwHjAcoBqgGIYWaHR0cDovL3gxLmMubGVu
Y3Iub3JnLzANBgkqhkiG9w0BAQsFAAOCAgEAjx66fDdLk5ywFn3CzA1w1qfylHUD
aEf0QZpXcJseddJGSfbUUOvbNR9N/QQ16K1lXl4VFyhmGXDT5Kdfcr0RvIIVrNxF
h4lqHtRRCP6RBRstqbZ2zURgqakn/Xip0iaQL0IdfHBZr396FgknniRYFckKORPG
yM3QKnd66gtMst8I5nkRQlAg/Jb+Gc3egIvuGKWboE1G89NTsN9LTDD3PLj0dUMr
OIuqVjLB8pEC6yk9enrlrqjXQgkLEYhXzq7dLafv5Vkig6Gl0nuuqjqfp0Q1bi1o
yVNAlXe6aUXw92CcghC9bNsKEO1+M52YY5+ofIXlS/SEQbvVYYBLZ5yeiglV6t3S
M6H+vTG0aP9YHzLn/KVOHzGQfXDP7qM5tkf+7diZe7o2fw6O7IvN6fsQXEQQj8TJ
UXJxv2/uJhcuy/tSDgXwHM8Uk34WNbRT7zGTGkQRX0gsbjAea/jYAoWv0ZvQRwpq
Pe79D/i7Cep8qWnA+7AE/3B3S/3dEEYmc0lpe1366A/6GEgk3ktr9PEoQrLChs6I
tu3wnNLB2euC8IKGLQFpGtOO/2/hiAKjyajaBP25w1jF0Wl8Bbqne3uZ2q1GyPFJ
YRmT7/OXpmOH/FVLtwS+8ng1cAmpCujPwteJZNcDG0sF2n/sc0+SQf49fdyUK0ty
+VUwFj9tmWxyR/M=
-----END CERTIFICATE-----)"
};
    
public:
    SettingsManager();
    
    void begin();
    /* bool loadSettings();
    bool saveSettings();*/
    void SetSettings(); 
    
    // Getters
    //String getSSID();      // first SSID from user settings or built-in defaults
    //String getPassword();  // password for the first SSID
    int getWiFiCount();    // number of user or default networks
    bool getWiFiCredential(int index, String& ssid, String& password);
    String getServerURL(); // base server URL from built-in defaults
    String getServerCAPem(); // CA certificate from built-in defaults
    String getDeviceID();
    String getMqttHost();
    uint16_t getMqttPort();
    String getMqttUser();
    String getMqttPass();
    int getSoilDryValue();
    int getSoilWetValue();
    float getWateringThreshold();
    unsigned long getPumpMaxRunTime();
    
    // Setters
    /* void setWiFiCredentials(const String& ssid, const String& password); // заменяет [0]
    bool addWiFiCredential(const String& ssid, const String& password);  // добавляет, до 10
    void clearWiFiCredentials();
    void setServerConfig(const String& url, const String& id); // url игнорируется
 */

    void setSoilCalibration(int dry, int wet);
    void setWateringThreshold(float threshold);
    void setPumpMaxRunTime(unsigned long runTime);
    
    String getStatus();
    
private:
    /* uint32_t calculateCRC();
    bool validateCRC(); */

    String generateDeviceIDFromMAC();
    //void loadBuiltinDefaults();
    String defaultServerCAPem;
    String getStringOrDefault(const char* value, const String& fallback);
};
