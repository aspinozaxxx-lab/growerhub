#include <Arduino.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "System/SettingsManager.h"
#include "Application.h"

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
PubSubClient mqttClient(espClient);

// Контроль частоты реконнектов MQTT.
unsigned long lastMqttReconnectAttempt = 0;
const unsigned long MQTT_RECONNECT_INTERVAL_MS = 5000;
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
static String buildDeviceTopic(const char* suffix);
static void publishAckAccepted(const String& correlationId, const char* statusText);
static void publishAckError(const String& correlationId, const char* reasonText);
static void mqttCallback(char* topic, byte* payload, unsigned int length);
static bool mqttReconnect();

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

    mqttClient.setCallback(mqttCallback);
    app.setMqttClient(&mqttClient);
    
    delay(3000);
    app.checkRelayStates(); // TODO: ?????????? ? MQTT state, ????? ?????? ????????? ?????????.
}

void loop() {
    // === Wi-Fi state machine ===
    updateWifiConnection();

    // === ?????'???????'??????????? ????????????? ?????>????? ???? ?'??????????? ===
    if (app.manualLoop()) {
        app.statePublishNow(); // ?????????? ??? ?? retained state, ????? ???????? ?????? auto-timeout.
        // TODO: ???????? ????????? ?????????? ACK/state ?? ?????????????? ?? ???????? ???????.
    }

    // === MQTT keepalive ===
    const bool wifiConnected = (WiFi.status() == WL_CONNECTED);
    if (wifiConnected) {
        if (mqttClient.connected()) {
            mqttClient.loop();
        } else {
            const unsigned long now = millis();
            if (now - lastMqttReconnectAttempt >= MQTT_RECONNECT_INTERVAL_MS) {
                lastMqttReconnectAttempt = now;
                Serial.println(F("MQTT not connected, retrying..."));
                if (mqttReconnect()) {
                    Serial.println(F("MQTT reconnected successfully."));
                    lastMqttReconnectAttempt = 0;
                }
            }
        }
    } else {
        if (mqttClient.connected()) {
            mqttClient.disconnect();
        }
        lastMqttReconnectAttempt = 0;
    }

    // === Heartbeat publish ===
    app.stateHeartbeatLoop(wifiConnected && mqttClient.connected());

    app.update();

    // ?>?'??????? ??????????, ??'???+?< ???? ???????'???'? CPU ???? 100%.
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
        if (mqttClient.connected()) {
            mqttClient.disconnect();
        }
        lastMqttReconnectAttempt = 0;
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

static String buildDeviceTopic(const char* suffix) {
    String topic = "gh/dev/";
    topic += g_settings.getDeviceID();
    topic += "/";
    topic += suffix;
    return topic;
}

static void publishAckAccepted(const String& correlationId, const char* statusText) {
    // ACK — одноразовое подтверждение. Retain всегда false, чтобы брокер не хранил старые ответы.
    if (!mqttClient.connected()) {
        Serial.println(F("Не удалось отправить ACK (accepted): MQTT не подключён, сообщение потеряно."));
        return;
    }

    const String status = statusText ? String(statusText) : String("");
    const String payload =
        String("{\"correlation_id\":\"") + correlationId +
        "\",\"result\":\"accepted\",\"status\":\"" + status + "\"}";

    Serial.print(F("Отправляем ACK (accepted) в брокер: "));
    Serial.println(payload);
    const String ackTopic = buildDeviceTopic("ack");
    mqttClient.publish(ackTopic.c_str(), payload.c_str(), false);
}

static void publishAckError(const String& correlationId, const char* reasonText) {
    if (!mqttClient.connected()) {
        Serial.println(F("Не удалось отправить ACK (error): MQTT не подключён, сообщение потеряно."));
        return;
    }

    const String reason = reasonText ? String(reasonText) : String("unknown error");
    const String payload =
        String("{\"correlation_id\":\"") + correlationId +
        "\",\"result\":\"error\",\"reason\":\"" + reason + "\"}";

    Serial.print(F("Отправляем ACK (error) в брокер: "));
    Serial.println(payload);
    const String ackTopic = buildDeviceTopic("ack");
    mqttClient.publish(ackTopic.c_str(), payload.c_str(), false);
}

static void mqttCallback(char* topic, byte* payload, unsigned int length) {
    // Критический шлюз: фронтенд -> сервер -> MQTT -> устройство. Любая ошибка парсинга влияет на работу реального насоса.
    // После обработки каждой команды шлём ACK publishAck*, чтобы FastAPI ответил клиенту из /wait-ack.
    Serial.println(F("----- MQTT команда -----"));
    Serial.print(F("Топик: "));
    Serial.println(topic);

    Serial.print(F("Полезная нагрузка: "));
    for (unsigned int i = 0; i < length; ++i) {
        Serial.print(static_cast<char>(payload[i]));
    }
    Serial.println();

    Serial.print(F("Длина полезной нагрузки (байт): "));
    Serial.println(length);

    StaticJsonDocument<256> doc;
    DeserializationError err = deserializeJson(doc, payload, length);
    if (err) {
        Serial.print(F("Не удалось распарсить JSON команды: "));
        Serial.println(err.c_str());
        publishAckError(String(""), "bad command format: invalid JSON");
        return;
    }

    const char* type = doc["type"];
    String correlationId = "";
    if (doc.containsKey("correlation_id") && doc["correlation_id"].is<const char*>()) {
        correlationId = String(doc["correlation_id"].as<const char*>());
    }

    if (!type) {
        Serial.println(F("Поле type отсутствует — команда не распознана."));
        publishAckError(correlationId, "bad command format: type missing");
        return;
    }

    const String commandType(type);
    if (commandType == "pump.start") {
        if (!doc.containsKey("duration_s") ||
            !(doc["duration_s"].is<uint32_t>() || doc["duration_s"].is<unsigned long>() || doc["duration_s"].is<int>())) {
            Serial.println(F("pump.start без duration_s или с некорректным типом — игнорируем."));
            publishAckError(correlationId, "bad command format: duration_s missing or invalid");
            return;
        }

        uint32_t durationSec = doc["duration_s"];
        if (durationSec == 0) {
            Serial.println(F("pump.start с duration_s=0 — игнорируем команду."));
            publishAckError(correlationId, "bad command format: duration_s missing or invalid");
            return;
        }

        Serial.print(F("Разбор pump.start: duration_s="));
        Serial.print(durationSec);
        Serial.print(F(", correlation_id="));
        Serial.println(correlationId);

        if (app.manualStart(durationSec, correlationId)) {
            app.statePublishNow();
        }
        if (correlationId.length() > 0) {
            publishAckAccepted(correlationId, "running");
        } else {
            Serial.println(F("pump.start без correlation_id — выполнили команду, но сообщаем серверу об ошибке формата."));
            publishAckError(String(""), "bad command format: correlation_id missing");
        }
        return;
    }

    if (commandType == "pump.stop") {
        Serial.print(F("Разбор pump.stop, correlation_id="));
        Serial.println(correlationId);

        if (app.manualStop(correlationId)) {
            app.statePublishNow();
        }
        if (correlationId.length() > 0) {
            publishAckAccepted(correlationId, "idle");
        } else {
            Serial.println(F("pump.stop без correlation_id — насос остановлен, но ответа без идентификатора недостаточно."));
            publishAckError(String(""), "bad command format: correlation_id missing");
        }
        return;
    }

    Serial.print(F("Нераспознанная команда type="));
    Serial.println(commandType);
    publishAckError(correlationId, "unsupported command type");
}

static bool mqttReconnect() {
    if (WiFi.status() != WL_CONNECTED) {
        return false;
    }

    String clientId = g_settings.getDeviceID();
    String host = g_settings.getMqttHost();
    uint16_t port = g_settings.getMqttPort();
    String user = g_settings.getMqttUser();
    String pass = g_settings.getMqttPass();

    mqttClient.setServer(host.c_str(), port);

    Serial.print(F("MQTT connect as "));
    Serial.println(clientId);

    if (mqttClient.connect(clientId.c_str(), user.c_str(), pass.c_str())) {
        Serial.println(F("MQTT connected, subscribing..."));
        String cmdTopic = buildDeviceTopic("cmd");
        if (mqttClient.subscribe(cmdTopic.c_str(), 1)) {
            Serial.print(F("Subscribed to "));
            Serial.println(cmdTopic);
            app.statePublishNow();
        } else {
            Serial.println(F("Failed to subscribe to command topic."));
        }
        return true;
    }

    Serial.print(F("MQTT connect failed, state="));
    Serial.println(mqttClient.state());
    return false;
}



