// Тонкая точка входа прошивки; логика вынесена в Application и Network/MQTTClient
#include <Arduino.h>
#include <WiFi.h>
#include <ArduinoJson.h>
#include "System/SettingsManager.h"
#include "Application.h"
#include "Network/MQTTClient.h"
#include "Network/WiFiService.h"
#include "System/SystemClock.h"

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
    Serial.println(F("GrowerHub Grovika ManualWatering v0.1 (MQTT step4)"));
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
        }
    });
    mqttClientManager.setPumpStatusProvider([&]() {
        return app.isManualPumpRunning();
    });
    mqttClientManager.setConnectedHandler([&]() {
        app.statePublishNow();
        app.resetHeartbeatTimer();
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
