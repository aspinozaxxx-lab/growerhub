#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include "Application.h"

// Уникальный идентификатор устройства одновременно служит MQTT clientId, чтобы брокер однозначно понимал,
// какое устройство подключается и можно было сопоставлять команды и состояния конкретному ESP32.
const char* DEVICE_ID = "ESP32_2C294C";

// Временные данные Wi-Fi; реальные креды прошьем отдельно перед установкой устройства в стойку.
const char* WIFI_SSID = "YOUR_WIFI";
const char* WIFI_PASS = "YOUR_PASS";

// Настройки брокера MQTT. Нельзя использовать 127.0.0.1, потому что этот адрес смотрит на сам ESP32,
// а нам нужна точка зрения устройства на Mosquitto в сети.
const char* MQTT_HOST = "192.168.0.11";
const uint16_t MQTT_PORT = 1883;

// Учетные данные тестового пользователя брокера.
const char* MQTT_USER = "mosquitto-admin";
const char* MQTT_PASS = "qazwsxedc";

// Командный топик, куда сервер шлет pump.start/pump.stop и другие команды управления.
const char* CMD_TOPIC = "gh/dev/ESP32_2C294C/cmd";

// Сетевой стек: аппаратный TCP клиент и MQTT клиент поверх него.
WiFiClient espClient;
PubSubClient mqttClient(espClient);

// Переменные для управления реконнектом MQTT. Храним таймстемп последней попытки, чтобы не спамить брокер
// и не лочить устройство постоянными connect().
unsigned long lastMqttReconnectAttempt = 0;
const unsigned long MQTT_RECONNECT_INTERVAL_MS = 5000;

WateringApplication app;

static void connectToWiFi();
static void ensureWifiConnected();
static void mqttCallback(char* topic, byte* payload, unsigned int length);
static bool mqttReconnect();

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println(F("GrowerHub ESP32 ManualWatering v0.1 (MQTT step1)"));
    Serial.print(F("Текущий DEVICE_ID: "));
    Serial.println(DEVICE_ID);

    // На первом шаге подключаемся к Wi-Fi в режиме STA и ждем IP, чтобы сразу после загрузки прошивки
    // можно было протестировать брокер из Serial Monitor.
    connectToWiFi();

    // Настраиваем MQTT-клиент: задаем адрес брокера и callback, который будет логировать входящие команды.
    mqttClient.setServer(MQTT_HOST, MQTT_PORT);
    mqttClient.setCallback(mqttCallback);

    if (mqttReconnect()) {
        Serial.println(F("MQTT подключение установлено и подписка оформлена."));
    } else {
        Serial.print(F("MQTT подключиться не удалось, код состояния: "));
        Serial.println(mqttClient.state());
    }

    // Запускаем основное приложение GrowerHub, где остаются сенсоры, HTTP и автоматические задачи.
    app.begin();
    delay(3000);
    app.checkRelayStates(); // TODO: заменить на аккуратную диагностику после интеграции управления насосом через MQTT.

    //app.factoryReset();
    // delay(3000);
    // app.testSensors();
    // delay(1000);
    // app.testActuators();
}

void loop() {
    // === Контроль Wi-Fi ===
    ensureWifiConnected();

    // === MQTT keepalive ===
    if (mqttClient.connected()) {
        mqttClient.loop();
    } else {
        const unsigned long now = millis();
        if (now - lastMqttReconnectAttempt >= MQTT_RECONNECT_INTERVAL_MS) {
            lastMqttReconnectAttempt = now;
            Serial.println(F("MQTT не подключен, пробуем реконнект..."));
            if (mqttReconnect()) {
                Serial.println(F("MQTT переподключение прошло успешно."));
                lastMqttReconnectAttempt = 0;
            }
        }
    }

    // TODO: Шаг 2 — разруливать pump.start/pump.stop, публиковать ACK и state в зависимости от результата.

    app.update();

    // Даем ESP32 немного отдохнуть, чтобы не держать CPU на 100%.
    delay(10);
}

static void connectToWiFi() {
    // Включаем режим станции — ESP32 должен подключаться к существующей точке, а не поднимать свою.
    WiFi.mode(WIFI_STA);
    Serial.print(F("Подключаемся к Wi-Fi SSID: "));
    Serial.println(WIFI_SSID);

    WiFi.begin(WIFI_SSID, WIFI_PASS);

    // Ждем установления соединения, печатая точки, чтобы видеть прогресс в Serial Monitor.
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
    Serial.println(F("Wi-Fi отключился, пытаемся переподключиться..."));
    connectToWiFi();
}

static void mqttCallback(char* topic, byte* payload, unsigned int length) {
    // Здесь в следующих шагах появится разбор pump.start/pump.stop, проверка correlation_id и контроль насоса.
    // На первом шаге ограничиваемся логированием входящих команд, чтобы проверить цепочку "сервер -> брокер -> устройство".
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
}

static bool mqttReconnect() {
    Serial.print(F("Пробуем подключиться к MQTT как "));
    Serial.println(DEVICE_ID);

    if (mqttClient.connect(DEVICE_ID, MQTT_USER, MQTT_PASS)) {
        Serial.println(F("MQTT соединение установлено, подписываемся на командный топик..."));
        // После любой потери соединения брокер забывает наши подписки, поэтому каждый раз оформляем subscribe заново.
        if (mqttClient.subscribe(CMD_TOPIC, 1)) {
            Serial.print(F("Подписались на "));
            Serial.print(CMD_TOPIC);
            Serial.println(F(" с QoS=1."));
        } else {
            Serial.println(F("Не удалось оформить подписку, проверим позже при следующей попытке."));
        }
        return true;
    }

    Serial.print(F("MQTT connect вернул ошибку, состояние клиента: "));
    Serial.println(mqttClient.state());
    return false;
}
