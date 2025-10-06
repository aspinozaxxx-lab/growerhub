#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Update.h>
#include <DHT.h>
#include <time.h>
#include <EEPROM.h>

// Настройки WiFi
const char* ssid = "JR";
const char* password = "qazwsxedc";

// Настройки сервера
const char* serverUrl = "http://192.168.0.11";
const char* deviceId = "esp32_watering_001";

// Пины
const int soilMoisturePin = 34;
const int relayPin = 4;
const int lightRelayPin = 5;
const int dhtPin = 15;
#define DHT_TYPE DHT22

// Структура для хранения времени
struct RTC_Time {
  uint8_t hour;
  uint8_t minute;
  uint8_t second;
  uint32_t lastSync;
};

// Структура для хранения настроек
struct DeviceSettings {
  float targetMoisture;
  int wateringDuration;
  int wateringTimeout;
  int lightOnHour;
  int lightOffHour;
  int lightDuration;
  uint32_t crc;
};

// Переменные
float soilMoisture = 0.0;
float airTemperature = 0.0;
float airHumidity = 0.0;
bool isWatering = false;
bool isLightOn = false;
unsigned long lastWateringTime = 0;
unsigned long lastStatusSend = 0;
unsigned long lastSettingsUpdate = 0;
unsigned long lastOTACheck = 0;
unsigned long lightStartTime = 0;
unsigned long lastLightCheck = 0;

// Настройки полива (значения по умолчанию)
float targetMoisture = 40.0;
int wateringDuration = 30;
int wateringTimeout = 300;

// Настройки освещения (значения по умолчанию)
int lightOnHour = 6;
int lightOffHour = 22;
int lightDuration = 16;

// OTA переменные
String currentFirmwareVersion = "1.0.0";
bool otaInProgress = false;

// RTC время
RTC_Time rtcTime = {0, 0, 0, 0};
bool timeSynced = false;

// EEPROM
DeviceSettings savedSettings;
#define EEPROM_SIZE sizeof(DeviceSettings)
#define SETTINGS_ADDR 0

HTTPClient http;
DHT dht(dhtPin, DHT_TYPE);

// Объявления функций
void connectToWiFi();
float readSoilMoisture();
void readDHT22();
void sendStatusToServer();
void updateSettingsFromServer();
void controlWatering();
void controlLighting();
void checkOTAUpdate();
void performOTAUpdate(String firmwareUrl);
void updateRTC();
bool syncWithNTP();
int getCurrentHour();
uint32_t calculateCRC(const DeviceSettings& settings);
void saveSettingsToEEPROM();
bool loadSettingsFromEEPROM();

void connectToWiFi() {
  Serial.print("Connecting to WiFi");
  WiFi.begin(ssid, password);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(1000);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nConnected to WiFi!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\nFailed to connect to WiFi!");
  }
}

float readSoilMoisture() {
  int analogValue = analogRead(soilMoisturePin);
  float moisture = map(analogValue, 0, 4095, 100, 0);
  return moisture;
}

void readDHT22() {
  float temp = dht.readTemperature();
  float hum = dht.readHumidity();
  
  if (!isnan(temp)) {
    airTemperature = temp;
  }
  if (!isnan(hum)) {
    airHumidity = hum;
  }
}

void sendStatusToServer() {
  if (WiFi.status() != WL_CONNECTED || otaInProgress) {
    connectToWiFi();
    return;
  }
  
  String url = String(serverUrl) + "/api/device/" + deviceId + "/status";
  
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  
  DynamicJsonDocument doc(1024);
  doc["device_id"] = deviceId;
  doc["soil_moisture"] = soilMoisture;
  doc["air_temperature"] = airTemperature;
  doc["air_humidity"] = airHumidity;
  doc["is_watering"] = isWatering;
  doc["is_light_on"] = isLightOn;
  if (lastWateringTime > 0) {
    doc["last_watering"] = lastWateringTime / 1000;
  }
  
  String jsonString;
  serializeJson(doc, jsonString);
  
  int httpResponseCode = http.POST(jsonString);
  
  if (httpResponseCode == 200) {
    Serial.println("Status sent to server");
  } else {
    Serial.print("Error sending status: ");
    Serial.println(httpResponseCode);
  }
  
  http.end();
}

void updateSettingsFromServer() {
  if (WiFi.status() != WL_CONNECTED || otaInProgress) {
    return;
  }
  
  String url = String(serverUrl) + "/api/device/" + deviceId + "/settings";
  
  http.begin(url);
  int httpResponseCode = http.GET();
  
  if (httpResponseCode == 200) {
    String response = http.getString();
    
    DynamicJsonDocument doc(1024);
    deserializeJson(doc, response);
    
    targetMoisture = doc["target_moisture"];
    wateringDuration = doc["watering_duration"];
    wateringTimeout = doc["watering_timeout"];
    lightOnHour = doc["light_on_hour"];
    lightOffHour = doc["light_off_hour"];
    lightDuration = doc["light_duration"];
    
    saveSettingsToEEPROM();
    
    Serial.println("Settings updated from server and saved to EEPROM");
  } else {
    Serial.print("Error getting settings: ");
    Serial.println(httpResponseCode);
    Serial.println("Using settings from EEPROM");
  }
  
  http.end();
}

void performOTAUpdate(String firmwareUrl) {
  Serial.println("Starting OTA update...");
  otaInProgress = true;
  
  http.begin(firmwareUrl);
  
  int httpCode = http.GET();
  if (httpCode == 200) {
    int contentLength = http.getSize();
    Serial.printf("Firmware size: %d bytes\n", contentLength);
    
    if (contentLength > 0) {
      bool canBegin = Update.begin(contentLength);
      if (canBegin) {
        Serial.println("Begin OTA. This may take 2 - 5 mins to complete. Things might be quiet for a while.");
        
        WiFiClient* client = http.getStreamPtr();
        size_t written = Update.writeStream(*client);
        
        if (written == contentLength) {
          Serial.println("Written : " + String(written) + " successfully");
        } else {
          Serial.println("Written only : " + String(written) + "/" + String(contentLength) + ". Retry?");
        }
        
        if (Update.end()) {
          Serial.println("OTA done!");
          if (Update.isFinished()) {
            Serial.println("Update successfully completed. Rebooting.");
            ESP.restart();
          } else {
            Serial.println("Update not finished? Something went wrong!");
          }
        } else {
          Serial.println("Error Occurred. Error #: " + String(Update.getError()));
        }
      } else {
        Serial.println("Not enough space to begin OTA");
      }
    } else {
      Serial.println("Content-Length was 0 or not found?");
    }
  } else {
    Serial.println("Error on HTTP request");
  }
  
  http.end();
  otaInProgress = false;
}

void checkOTAUpdate() {
  if (WiFi.status() != WL_CONNECTED || otaInProgress) {
    return;
  }
  
  String url = String(serverUrl) + "/api/device/" + deviceId + "/firmware";
  
  http.begin(url);
  int httpResponseCode = http.GET();
  
  if (httpResponseCode == 200) {
    String response = http.getString();
    
    DynamicJsonDocument doc(256);
    deserializeJson(doc, response);
    
    if (doc["update_available"]) {
      Serial.println("OTA Update available!");
      String firmwareUrl = doc["firmware_url"].as<String>();
      performOTAUpdate(firmwareUrl);
    }
  }
  
  http.end();
}

void controlWatering() {
  unsigned long currentTime = millis();
  
  if (isWatering) {
    if (currentTime - lastWateringTime >= (wateringDuration * 1000)) {
      digitalWrite(relayPin, HIGH);
      isWatering = false;
      Serial.println("Watering stopped");
      sendStatusToServer();
    }
    return;
  }
  
  if (lastWateringTime > 0 && 
      currentTime - lastWateringTime < (wateringTimeout * 1000)) {
    return;
  }
  
  if (soilMoisture < targetMoisture) {
    digitalWrite(relayPin, LOW);
    isWatering = true;
    lastWateringTime = currentTime;
    Serial.println("Watering started");
    sendStatusToServer();
  }
}

void updateRTC() {
  static uint32_t lastUpdate = 0;
  uint32_t currentMillis = millis();
  
  if (currentMillis - lastUpdate >= 1000) {
    lastUpdate = currentMillis;
    
    rtcTime.second++;
    if (rtcTime.second >= 60) {
      rtcTime.second = 0;
      rtcTime.minute++;
      if (rtcTime.minute >= 60) {
        rtcTime.minute = 0;
        rtcTime.hour++;
        if (rtcTime.hour >= 24) {
          rtcTime.hour = 0;
        }
      }
    }
  }
}

bool syncWithNTP() {
  if (WiFi.status() != WL_CONNECTED) {
    return false;
  }
  
  configTime(10800, 0, "pool.ntp.org", "time.nist.gov");
  
  struct tm timeinfo;
  if(!getLocalTime(&timeinfo, 10000)){
    Serial.println("NTP sync failed");
    return false;
  }
  
  rtcTime.hour = timeinfo.tm_hour;
  rtcTime.minute = timeinfo.tm_min;
  rtcTime.second = timeinfo.tm_sec;
  rtcTime.lastSync = millis();
  timeSynced = true;
  
  Serial.print("Time synced: ");
  Serial.print(rtcTime.hour);
  Serial.print(":");
  Serial.print(rtcTime.minute);
  Serial.print(":");
  Serial.println(rtcTime.second);
  
  return true;
}

int getCurrentHour() {
  return rtcTime.hour;
}

uint32_t calculateCRC(const DeviceSettings& settings) {
  uint32_t crc = 0;
  uint8_t* data = (uint8_t*)&settings;
  for(size_t i = 0; i < sizeof(DeviceSettings) - sizeof(uint32_t); i++) {
    crc += data[i];
  }
  return crc;
}

void saveSettingsToEEPROM() {
  savedSettings.targetMoisture = targetMoisture;
  savedSettings.wateringDuration = wateringDuration;
  savedSettings.wateringTimeout = wateringTimeout;
  savedSettings.lightOnHour = lightOnHour;
  savedSettings.lightOffHour = lightOffHour;
  savedSettings.lightDuration = lightDuration;
  savedSettings.crc = calculateCRC(savedSettings);
  
  EEPROM.put(SETTINGS_ADDR, savedSettings);
  EEPROM.commit();
  
  Serial.println("Settings saved to EEPROM");
}

bool loadSettingsFromEEPROM() {
  EEPROM.get(SETTINGS_ADDR, savedSettings);
  
  if(calculateCRC(savedSettings) != savedSettings.crc) {
    Serial.println("EEPROM settings corrupted, using defaults");
    return false;
  }
  
  targetMoisture = savedSettings.targetMoisture;
  wateringDuration = savedSettings.wateringDuration;
  wateringTimeout = savedSettings.wateringTimeout;
  lightOnHour = savedSettings.lightOnHour;
  lightOffHour = savedSettings.lightOffHour;
  lightDuration = savedSettings.lightDuration;
  
  Serial.println("Settings loaded from EEPROM");
  return true;
}

void controlLighting() {
  int currentHour = getCurrentHour();
  
  bool shouldLightBeOn = (currentHour >= lightOnHour && currentHour < lightOffHour);
  
  Serial.print("Light control: Hour=");
  Serial.print(currentHour);
  Serial.print(", Should be ON: ");
  Serial.println(shouldLightBeOn ? "YES" : "NO");
  
  if (shouldLightBeOn && !isLightOn) {
    digitalWrite(lightRelayPin, LOW);
    isLightOn = true;
    lightStartTime = millis();
    Serial.println("Light turned ON");
    sendStatusToServer();
  } else if (!shouldLightBeOn && isLightOn) {
    digitalWrite(lightRelayPin, HIGH);
    isLightOn = false;
    Serial.println("Light turned OFF");
    sendStatusToServer();
  }
}

void setup() {
  Serial.begin(115200);
  
  EEPROM.begin(EEPROM_SIZE);
  
  pinMode(relayPin, OUTPUT);
  pinMode(lightRelayPin, OUTPUT);
  digitalWrite(relayPin, HIGH);
  digitalWrite(lightRelayPin, HIGH);
  
  dht.begin();
  
  if(!loadSettingsFromEEPROM()) {
    Serial.println("Using default settings");
  }
  
  connectToWiFi();
  
  if(WiFi.status() == WL_CONNECTED) {
    syncWithNTP();
  } else {
    Serial.println("No WiFi, using autonomous RTC");
  }
  
  updateSettingsFromServer();
}

void loop() {
  unsigned long currentTime = millis();
  
  updateRTC();
  
  soilMoisture = readSoilMoisture();
  readDHT22();
  
  if (currentTime - rtcTime.lastSync > 86400000 && WiFi.status() == WL_CONNECTED) {
    syncWithNTP();
  }
  
  if (currentTime - lastWateringTime > 10000 && !otaInProgress) {
    controlWatering();
  }
  
  if (currentTime - lastLightCheck > 60000 && !otaInProgress) {
    controlLighting();
    lastLightCheck = currentTime;
  }
  
  if (currentTime - lastStatusSend > 30000 && !otaInProgress && WiFi.status() == WL_CONNECTED) {
    sendStatusToServer();
    lastStatusSend = currentTime;
  }
  
  if (currentTime - lastSettingsUpdate > 60000 && !otaInProgress && WiFi.status() == WL_CONNECTED) {
    updateSettingsFromServer();
    lastSettingsUpdate = currentTime;
  }
  
  if (currentTime - lastOTACheck > 300000 && !otaInProgress && WiFi.status() == WL_CONNECTED) {
    checkOTAUpdate();
    lastOTACheck = currentTime;
  }
  
  delay(1000);
}