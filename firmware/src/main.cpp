// Тонкая точка входа прошивки; логика вынесена в Application и Network/MQTTClient
#include <Arduino.h>
#include <WiFi.h>
#include <ArduinoJson.h>
#include "System/SettingsManager.h"
#include "Application.h"
#include "Network/MQTTClient.h"
#include "Network/WiFiService.h"
#include "System/SystemClock.h"
#include <nvs_flash.h>

class EspRebooter : public SystemMonitor::IRebooter {
public:
    void restart() override {
        ESP.restart();
    }
};

// Уникальный идентификатор устройства = MQTT clientId. Сервер и брокер используют его,
// чтобы понять, какое железо получило команду и кто должен вернуть ACK/state.
// Версия прошивки, публикуемая в state, чтобы фронтенд видел активную сборку.
const char* FW_VERSION = "grovika-alpha1";

// Временные Wi-Fi креды. Перед боевой прошивкой подставим реальные SSID/PASS.

// Адрес брокера Mosquitto. Нельзя использовать 127.0.0.1, потому что с точки зрения ESP32 это локальная петля.

// Каналы MQTT: команды, ACK и retained state.

// Сетевой стек.
SettingsManager settings;
WiFiService wifi(settings);
WiFiClient espClient;
Network::MQTTClient mqttClientManager(settings, espClient);
SystemClock systemClock(nullptr, nullptr, nullptr, nullptr);
EspRebooter espRebooter;

// --- Периодическая отправка state (heartbeat) ---

WateringApplication app;

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println(F("GrowerHub Grovika ManualWatering v0.2 (OTA)"));
    // Gotovim NVS dlya markerov OTA/diagnostiki.
    esp_err_t nvsErr = nvs_flash_init();
    if (nvsErr == ESP_ERR_NVS_NO_FREE_PAGES || nvsErr == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        nvs_flash_erase();
        nvsErr = nvs_flash_init();
    }
    if (nvsErr != ESP_OK) {
        Serial.printf("NVS: init fail err=%d\n", nvsErr);
    } else {
        Serial.println(F("NVS: init ok."));
    }
    app.setSystemClock(&systemClock);
    app.begin();
    settings.begin();
    
    String deviceId = settings.getDeviceID();
    Serial.print(F("Current DEVICE_ID: "));
    Serial.println(deviceId);
    
    // Настраиваем Wi-Fi сервис: режим STA и синхронная попытка подключения.
    WiFi.mode(WIFI_STA);
    // Передаём HTTP клиенту флаг доступности сети из WiFiService.
    WateringHTTPClient::setWiFiOnlineProvider([]() { return wifi.isOnline(); });
    wifi.connectSync();
    systemClock.begin();
    systemClock.dumpStatusToSerial();
    wifi.startAsyncReconnectIfNeeded();

    mqttClientManager.begin();
    mqttClientManager.setCommandHandler([&](const String& commandType, const JsonDocument& doc, const String& correlationId) {
        if (commandType == "pump.start") {
            uint32_t durationSec = doc["duration_s"].as<uint32_t>();
            app.manualStart(durationSec, correlationId);
            app.statePublishNow();
        } else if (commandType == "pump.stop") {
            app.manualStop(correlationId);
            app.statePublishNow();
        } else if (commandType == "reboot") {
            app.requestReboot(correlationId);
        } else if (commandType == "ota") {
            const char* urlPtr = doc.containsKey("url") && doc["url"].is<const char*>()
                ? doc["url"].as<const char*>()
                : nullptr;
            const char* versionPtr = doc.containsKey("version") && doc["version"].is<const char*>()
                ? doc["version"].as<const char*>()
                : "";
            const char* shaPtr = doc.containsKey("sha256") && doc["sha256"].is<const char*>()
                ? doc["sha256"].as<const char*>()
                : "";
            const String url = urlPtr ? String(urlPtr) : String("");
            const String version = versionPtr ? String(versionPtr) : String("");
            const String sha = shaPtr ? String(shaPtr) : String("");
            if (!app.startPullOta(url, version, sha)) {
                Serial.println(F("OTA(PULL): komanda ne zapushchena."));
            }
        }
    });
    mqttClientManager.setPumpStatusProvider([&]() {
        return app.isManualPumpRunning();
    });
    mqttClientManager.setConnectedHandler([&]() {
        app.statePublishNow();
        app.resetHeartbeatTimer();
        app.publishPendingOtaAckIfAny();
    });
    app.setMqttClient(&mqttClientManager.client());

    SystemMonitor& monitor = app.getSystemMonitor();
    monitor.setPumpStatusProvider([&]() {
        return app.isManualPumpRunning();
    });
    monitor.setAckPublisher([&](const String& correlationId, const char* status, bool accepted) {
        mqttClientManager.publishAckStatus(correlationId, status, accepted);
    });
    monitor.setStatePublisher([&](bool retained) {
        app.statePublishNow(retained);
    });
    monitor.setRebooter(&espRebooter);
    
    delay(3000);
    app.checkRelayStates(); // TODO: сверить вывод с MQTT state и убрать после стабилизации схемы.
}

void loop() {
    // === Wi-Fi service loop (поддерживает прежние ретраи/бэкоффы) ===
    wifi.loop(millis());

    // === Мониторинг ручного полива и таймаутов ===
    if (app.manualLoop()) {
        app.statePublishNow(); // Обновляем retained state, чтобы фронтенд увидел auto-timeout.
        // TODO: обсудить повторную публикацию ACK/state при автоостановке с backend-командой.
    }

    // === MQTT keepalive ===
    const bool wifiConnected = wifi.isOnline();
    mqttClientManager.loop(wifiConnected);

    // === Heartbeat publish ===
    app.stateHeartbeatLoop(wifiConnected && mqttClientManager.isConnected());

    systemClock.loop();
    app.update();

    // Даём контрольной петле паузу, чтобы не грузить CPU на 100%.
    delay(10);
}
