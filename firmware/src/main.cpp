// Тонкая точка входа прошивки; логика вынесена в Application и Network/MQTTClient
#include <Arduino.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <ArduinoJson.h>
#include "System/SettingsManager.h"
#include "Application.h"
#include "Network/MQTTClient.h"

// Уникальный идентификатор устройства = MQTT clientId. Сервер и брокер используют его,
// чтобы понять, какое железо получило команду и кто должен вернуть ACK/state.
// Версия прошивки, публикуемая в state, чтобы фронтенд видел активную сборку.
const char* FW_VERSION = "grovika-alpha1";

// Временные Wi-Fi креды. Перед боевой прошивкой подставим реальные SSID/PASS.

// Адрес брокера Mosquitto. Нельзя использовать 127.0.0.1, потому что с точки зрения ESP32 это локальная петля.

// Каналы MQTT: команды, ACK и retained state.

// Сетевой стек.
SettingsManager g_settings;
WiFiMulti g_wifiMulti;
WiFiClient espClient;
Network::MQTTClient mqttClientManager(g_settings, espClient);
static const unsigned long WIFI_RETRY_INTERVAL_MS = 5000;
static const unsigned long WIFI_BACKOFF_INTERVAL_MS = 20000;
static unsigned long g_wifiNextAttemptMillis = 0;
static uint16_t g_wifiAttemptCounter = 0;
static bool g_wifiLoggedNoNetworks = false;
static wl_status_t g_lastWifiStatus = WL_IDLE_STATUS;

// --- Периодическая отправка state (heartbeat) ---

WateringApplication app;

static void configureWifiNetworks();
static void updateWifiConnection();

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println(F("GrowerHub Grovika ManualWatering v0.1 (MQTT step4)"));
    app.begin();
    g_settings.begin();
    
    String deviceId = g_settings.getDeviceID();
    Serial.print(F("Current DEVICE_ID: "));
    Serial.println(deviceId);
    
    WiFi.mode(WIFI_STA);
    configureWifiNetworks();
    g_wifiNextAttemptMillis = 0;
    g_wifiAttemptCounter = 0;
    g_wifiLoggedNoNetworks = false;
    g_lastWifiStatus = WiFi.status();
    updateWifiConnection();

    mqttClientManager.begin();
    mqttClientManager.setCommandHandler([&](const String& commandType, const JsonDocument& doc, const String& correlationId) {
        if (commandType == "pump.start") {
            uint32_t durationSec = doc["duration_s"].as<uint32_t>();
            app.manualStart(durationSec, correlationId);
            app.statePublishNow();
        } else if (commandType == "pump.stop") {
            app.manualStop(correlationId);
            app.statePublishNow();
        }
    });
    mqttClientManager.setConnectedHandler([&]() {
        app.statePublishNow();
        app.resetHeartbeatTimer();
    });
    app.setMqttClient(&mqttClientManager.client());
    
    delay(3000);
    app.checkRelayStates(); // TODO: сверить вывод с MQTT state и убрать после стабилизации схемы.
}

void loop() {
    // === Wi-Fi state machine ===
    updateWifiConnection();

    // === Мониторинг ручного полива и таймаутов ===
    if (app.manualLoop()) {
        app.statePublishNow(); // Обновляем retained state, чтобы фронтенд увидел auto-timeout.
        // TODO: обсудить повторную публикацию ACK/state при автоостановке с backend-командой.
    }

    // === MQTT keepalive ===
    const bool wifiConnected = (WiFi.status() == WL_CONNECTED);
    mqttClientManager.loop(wifiConnected);

    // === Heartbeat publish ===
    app.stateHeartbeatLoop(wifiConnected && mqttClientManager.isConnected());

    app.update();

    // Даём контрольной петле паузу, чтобы не грузить CPU на 100%.
    delay(10);
}


static void configureWifiNetworks() {
    // WiFiMulti не имеет cleanAPlist() в ESP32 core.
    // Мы просто пересоздаём список точек один раз в setup().
    WiFi.setAutoReconnect(true);   

    const int totalNetworks = g_settings.getWiFiCount();
    if (totalNetworks <= 0) {
        Serial.println(F("No Wi-Fi networks configured (user or defaults)."));
        g_wifiLoggedNoNetworks = true;
        return;
    }

    for (int i = 0; i < totalNetworks; ++i) {
        String ssid;
        String password;
        if (!g_settings.getWiFiCredential(i, ssid, password)) {
            continue;
        }
        if (ssid.length() == 0) {
            continue;
        }
        g_wifiMulti.addAP(ssid.c_str(), password.c_str());
        Serial.print(F("Configured Wi-Fi network: "));
        Serial.println(ssid);
    }

    g_wifiLoggedNoNetworks = false;
}

static void updateWifiConnection() {
    const unsigned long now = millis();
    const wl_status_t status = WiFi.status();

    if (status == WL_CONNECTED) {
        if (g_lastWifiStatus != WL_CONNECTED) {
            Serial.print(F("Wi-Fi connected, IP: "));
            Serial.print(WiFi.localIP());
            Serial.print(F(" Wi-Fi AP: "));
            Serial.println(WiFi.SSID());
        }
        g_wifiAttemptCounter = 0;
        g_wifiNextAttemptMillis = now + WIFI_RETRY_INTERVAL_MS;
        g_lastWifiStatus = WL_CONNECTED;
        return;
    }

    if (g_lastWifiStatus == WL_CONNECTED) {
        Serial.println(F("Wi-Fi connection lost, will retry via WiFiMulti."));
    }
    g_lastWifiStatus = status;

    if (now < g_wifiNextAttemptMillis) {
        return;
    }

    const int totalNetworks = g_settings.getWiFiCount();
    if (totalNetworks <= 0) {
        if (!g_wifiLoggedNoNetworks) {
            Serial.println(F("No Wi-Fi networks available for WiFiMulti, backing off."));
            g_wifiLoggedNoNetworks = true;
        }
        g_wifiNextAttemptMillis = now + WIFI_BACKOFF_INTERVAL_MS;
        return;
    }
    g_wifiLoggedNoNetworks = false;

    Serial.println(F("WiFiMulti attempting connection..."));
    wl_status_t result = (wl_status_t) g_wifiMulti.run();
    if (result == WL_CONNECTED) {
        Serial.print(F("Wi-Fi connected, IP: "));
        Serial.print(WiFi.localIP());
        Serial.print(F(" Wi-Fi AP: "));
        Serial.println(WiFi.SSID());
        g_wifiAttemptCounter = 0;
        g_wifiNextAttemptMillis = now + WIFI_RETRY_INTERVAL_MS;
        g_lastWifiStatus = WL_CONNECTED;
        return;
    }

    g_wifiAttemptCounter++;
    if (g_wifiAttemptCounter >= static_cast<uint16_t>(totalNetworks)) {
        Serial.println(F("Failed to connect to all Wi-Fi networks, backing off."));
        g_wifiAttemptCounter = 0;
        g_wifiNextAttemptMillis = now + WIFI_BACKOFF_INTERVAL_MS;
    } else {
        g_wifiNextAttemptMillis = now + WIFI_RETRY_INTERVAL_MS;
    }
}

