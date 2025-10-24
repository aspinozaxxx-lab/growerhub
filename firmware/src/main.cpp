#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "Application.h"

// Уникальный идентификатор устройства = MQTT clientId. Сервер и брокер используют его,
// чтобы понять, какое железо получило команду и кто должен вернуть ACK/state.
const char* DEVICE_ID = "ESP32_2C294C";
// Версия прошивки, публикуемая в state, чтобы фронтенд видел активную сборку.
const char* FW_VERSION = "esp32-alpha1";

// Временные Wi-Fi креды. Перед боевой прошивкой подставим реальные SSID/PASS.
const char* WIFI_SSID = "YOUR_WIFI";
const char* WIFI_PASS = "YOUR_PASS";

// Адрес брокера Mosquitto. Нельзя использовать 127.0.0.1, потому что с точки зрения ESP32 это локальная петля.
const char* MQTT_HOST = "192.168.0.11";
const uint16_t MQTT_PORT = 1883;

// Учётка Mosquitto для отладки.
const char* MQTT_USER = "mosquitto-admin";
const char* MQTT_PASS = "qazwsxedc";

// Каналы MQTT: команды, ACK и retained state.
const char* CMD_TOPIC = "gh/dev/ESP32_2C294C/cmd";   // Сервер шлёт сюда pump.start/pump.stop.
const char* ACK_TOPIC = "gh/dev/ESP32_2C294C/ack";   // Устройство подтверждает приём команд (без retain).
const char* STATE_TOPIC = "gh/dev/ESP32_2C294C/state"; // Retained state: сервер/фронт читают текущее состояние устройства.

// Сетевой стек.
WiFiClient espClient;
PubSubClient mqttClient(espClient);

// Контроль частоты реконнектов MQTT.
unsigned long lastMqttReconnectAttempt = 0;
const unsigned long MQTT_RECONNECT_INTERVAL_MS = 5000;

// Текущее состояние ручного полива. Эти поля понадобятся шагам 3+:
//  - ACK (чтобы ответить accepted/error с нужным correlation_id),
//  - state (retained снимок для фронтенда),
//  - автостоп/вотчдоги (когда появятся таймеры и более сложная логика).
static bool g_isWatering = false;              // true, если насос включён по manual watering.
static uint32_t g_wateringDurationSec = 0;     // Сколько секунд полива попросили через pump.start.
static uint32_t g_wateringStartMillis = 0;     // Момент включения насоса (millis) для таймаута.
static String g_activeCorrelationId = "";      // correlation_id последней команды pump.start.
static String g_wateringStartIso8601 = "";     // ISO8601 времени старта. Пока заглушка, позже будет реальное UTC.

WateringApplication app;

static void connectToWiFi();
static void ensureWifiConnected();
static void startManualWatering(uint32_t durationSec, const String& correlationId);
static void stopManualWatering(const String& correlationId);
static void publishAckAccepted(const String& correlationId, const char* statusText);
static void publishAckError(const String& correlationId, const char* reasonText);
static void publishCurrentState();
static void mqttCallback(char* topic, byte* payload, unsigned int length);
static bool mqttReconnect();

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println(F("GrowerHub ESP32 ManualWatering v0.1 (MQTT step4)"));
    Serial.print(F("Текущий DEVICE_ID: "));
    Serial.println(DEVICE_ID);

    // Подключаемся к Wi-Fi как станция и ждём IP, чтобы сразу тестировать MQTT-цепочку.
    connectToWiFi();

    mqttClient.setServer(MQTT_HOST, MQTT_PORT);
    mqttClient.setCallback(mqttCallback);

    if (mqttReconnect()) {
        Serial.println(F("MQTT подключение установлено, подписка активна."));
    } else {
        Serial.print(F("MQTT подключиться не удалось, код состояния клиента: "));
        Serial.println(mqttClient.state());
    }

    // Стартуем основное приложение: сенсоры, HTTP, планировщик задач и прочие подсистемы GrowerHub.
    app.begin();
    delay(3000);
    app.checkRelayStates(); // TODO: синхронизировать с MQTT state, когда появится полноценная диагностика.
}

void loop() {
    // === Контроль Wi-Fi ===
    ensureWifiConnected();

    // === Автоостановка ручного полива по таймеру ===
    if (g_isWatering && g_wateringDurationSec > 0) {
        const unsigned long elapsedMs = millis() - g_wateringStartMillis;
        const unsigned long plannedMs = static_cast<unsigned long>(g_wateringDurationSec) * 1000UL;
        if (elapsedMs >= plannedMs) {
            Serial.println(F("Полив завершён по таймеру duration_s, выключаем насос (auto-timeout)."));
            stopManualWatering(String("auto-timeout"));
            publishCurrentState(); // Важно: фронтенд должен увидеть, что полив завершился и кнопки можно разблокировать.
            // TODO: когда появится логика финального ACK/state о завершении по таймеру, реализовать её здесь.
        }
    }

    // === MQTT keepalive ===
    if (mqttClient.connected()) {
        mqttClient.loop();
    } else {
        const unsigned long now = millis();
        if (now - lastMqttReconnectAttempt >= MQTT_RECONNECT_INTERVAL_MS) {
            lastMqttReconnectAttempt = now;
            Serial.println(F("MQTT не подключен, пробуем переподключиться..."));
            if (mqttReconnect()) {
                Serial.println(F("MQTT переподключение успешно, подписка восстановлена."));
                lastMqttReconnectAttempt = 0;
            }
        }
    }

    app.update();

    // Лёгкая пауза, чтобы не держать CPU на 100%.
    delay(10);
}

static void connectToWiFi() {
    WiFi.mode(WIFI_STA);
    Serial.print(F("Подключаемся к Wi-Fi SSID: "));
    Serial.println(WIFI_SSID);

    WiFi.begin(WIFI_SSID, WIFI_PASS);

    while (WiFi.status() != WL_CONNECTED) {
        Serial.print('.');
        delay(500);
    }
    Serial.println();
    Serial.print(F("Wi-Fi подключен, локальный IP: "));
    Serial.println(WiFi.localIP());
}

static void ensureWifiConnected() {
    if (WiFi.status() == WL_CONNECTED) {
        return;
    }
    Serial.println(F("Wi-Fi отключился, переподключаемся..."));
    connectToWiFi();
}

static void startManualWatering(uint32_t durationSec, const String& correlationId) {
    if (g_isWatering) {
        Serial.println(F("pump.start проигнорирован: насос уже включён вручную, держим текущую сессию."));
        return;
    }
    if (durationSec == 0) {
        Serial.println(F("Запрос pump.start с duration_s=0 — насос не включаем."));
        return;
    }

    // TODO: уточнить инверсию реле. Сейчас считаем, что true = включить насос (через ActuatorManager).
    app.setManualPumpState(true);

    g_isWatering = true;
    g_wateringDurationSec = durationSec;
    g_wateringStartMillis = millis();
    g_activeCorrelationId = correlationId;

    // TODO: подставить реальное UTC время старта, когда появится синхронизация (NTP/RTC).
    g_wateringStartIso8601 = "1970-01-01T00:00:00Z";

    Serial.print(F("Запускаем ручной полив на "));
    Serial.print(durationSec);
    Serial.print(F(" секунд, correlation_id="));
    Serial.println(correlationId);

    publishCurrentState();
}

static void stopManualWatering(const String& correlationId) {
    if (!g_isWatering) {
        Serial.println(F("Получили pump.stop, но насос уже был выключен. Обнуляем состояние на всякий случай."));
    }

    app.setManualPumpState(false);

    g_isWatering = false;
    g_wateringDurationSec = 0;
    g_wateringStartMillis = 0;
    g_activeCorrelationId = "";
    g_wateringStartIso8601 = "";

    Serial.print(F("Полив остановлен. Источник остановки: "));
    Serial.println(correlationId.length() ? correlationId : String("не указан (pump.stop без correlation_id)"));

    publishCurrentState();
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
    mqttClient.publish(ACK_TOPIC, payload.c_str(), false);
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
    mqttClient.publish(ACK_TOPIC, payload.c_str(), false);
}

static void publishCurrentState() {
    // Retained state (device shadow) устройства. Брокер хранит последнее сообщение,
    // сервер читает его, кеширует и отдаёт фронтенду через /api/manual-watering/status.
    // Именно по этому снимку UI понимает: контроллер онлайн или оффлайн, работает ли насос,
    // можно ли разблокировать кнопки или показать прогресс полива.
    if (!mqttClient.connected()) {
        Serial.println(F("Не удалось отправить state: MQTT не подключён, снимок не обновлён."));
        return;
    }

    const bool hasCorrelation = g_activeCorrelationId.length() > 0;
    const bool hasStartIso = g_wateringStartIso8601.length() > 0;

    const String status = g_isWatering ? "running" : "idle";
    const String durationField = g_isWatering ? String(g_wateringDurationSec) : String("null");
    const String startedField = g_isWatering && hasStartIso
        ? String("\"") + g_wateringStartIso8601 + "\""
        : String("null");
    const String correlationField = g_isWatering && hasCorrelation
        ? String("\"") + g_activeCorrelationId + "\""
        : String("null");

    String payload = "{";
    payload += "\"manual_watering\":{";
    payload += "\"status\":\"" + status + "\",";
    payload += "\"duration_s\":" + durationField + ",";
    payload += "\"started_at\":" + startedField + ",";
    payload += "\"correlation_id\":" + correlationField;
    payload += "},";
    payload += "\"fw\":\"" + String(FW_VERSION) + "\"";
    payload += "}";

    Serial.print(F("Отправляем state (retained) в брокер: "));
    Serial.println(payload);
    mqttClient.publish(STATE_TOPIC, payload.c_str(), true); // retain=true: брокер хранит снимок.
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

        startManualWatering(durationSec, correlationId);
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

        stopManualWatering(correlationId);
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
    Serial.print(F("Пробуем подключиться к MQTT как "));
    Serial.println(DEVICE_ID);

    if (mqttClient.connect(DEVICE_ID, MQTT_USER, MQTT_PASS)) {
        Serial.println(F("MQTT соединение установлено, подписываемся на командный топик..."));
        // После реконнекта брокер забывает подписки, поэтому оформляем их заново.
        if (mqttClient.subscribe(CMD_TOPIC, 1)) {
            Serial.print(F("Подписались на "));
            Serial.print(CMD_TOPIC);
            Serial.println(F(" с QoS=1."));
            // После успешной подписки объявляем текущее состояние, чтобы сервер видел устройство онлайн сразу после реконнекта.
            publishCurrentState();
        } else {
            Serial.println(F("Не удалось подписаться на командный топик — попробуем снова при следующем реконнекте."));
        }
        return true;
    }

    Serial.print(F("MQTT connect вернул ошибку, состояние клиента: "));
    Serial.println(mqttClient.state());
    return false;
}
