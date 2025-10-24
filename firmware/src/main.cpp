#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "Application.h"

// Уникальный идентификатор устройства одновременно является MQTT clientId. Так сервер и брокер
// однозначно понимают, какое железо с ними общается, и смогут сопоставлять входящие ACK/state (шаги 3–4).
const char* DEVICE_ID = "ESP32_2C294C";

// Временные Wi-Fi креды. В продакшене перед прошивкой нужно будет прописать реальные SSID/PASS.
const char* WIFI_SSID = "YOUR_WIFI";
const char* WIFI_PASS = "YOUR_PASS";

// Настройки MQTT брокера. Нельзя использовать 127.0.0.1 — этот IP для самого ESP32.
// Указываем реальный адрес Mosquitto в локальной сети, чтобы контроллер нашёл брокер.
const char* MQTT_HOST = "192.168.0.11";
const uint16_t MQTT_PORT = 1883;

// Учетные данные тестового пользователя Mosquitto.
const char* MQTT_USER = "mosquitto-admin";
const char* MQTT_PASS = "qazwsxedc";

// Командный топик для ручного полива. Сервер публикует pump.start/pump.stop именно сюда.
const char* CMD_TOPIC = "gh/dev/ESP32_2C294C/cmd";

// Сетевой стек: TCP клиент и MQTT клиент.
WiFiClient espClient;
PubSubClient mqttClient(espClient);

// Контроль реконнекта MQTT, чтобы не забивать брокер попытками каждую миллисекунду.
unsigned long lastMqttReconnectAttempt = 0;
const unsigned long MQTT_RECONNECT_INTERVAL_MS = 5000;

// Текущее состояние ручного полива. Эти переменные понадобятся:
// - на шаге 3 для формирования корректного ACK (успех/отказ + correlation_id),
// - на шаге 4 для публикации state (running/idle, оставшееся время),
// - на шаге 5 для автоматического отключения по таймеру и watchdog'ам.
static bool g_isWatering = false;              // true, когда насос включен именно по manual watering.
static uint32_t g_wateringDurationSec = 0;     // Количество секунд, которое попросили поливать.
static uint32_t g_wateringStartMillis = 0;     // Метка millis(), когда включили насос.
static String g_activeCorrelationId = "";      // correlation_id последней pump.start (для ACK/state).

WateringApplication app;

static void connectToWiFi();
static void ensureWifiConnected();
static void startManualWatering(uint32_t durationSec, const String& correlationId);
static void stopManualWatering(const String& correlationId);
static void mqttCallback(char* topic, byte* payload, unsigned int length);
static bool mqttReconnect();

void setup() {
    Serial.begin(115200);
    Serial.println();
    Serial.println(F("GrowerHub ESP32 ManualWatering v0.1 (MQTT step2)"));
    Serial.print(F("Текущий DEVICE_ID: "));
    Serial.println(DEVICE_ID);

    // Подключаемся к Wi-Fi как станция и дожидаемся IP, чтобы сразу тестировать MQTT.
    connectToWiFi();

    // Готовим MQTT клиент: прописываем брокер и callback до старта основной логики.
    mqttClient.setServer(MQTT_HOST, MQTT_PORT);
    mqttClient.setCallback(mqttCallback);

    if (mqttReconnect()) {
        Serial.println(F("MQTT подключение установлено и оформлена подписка на командный топик."));
    } else {
        Serial.print(F("MQTT подключиться не удалось, код состояния клиента: "));
        Serial.println(mqttClient.state());
    }

    // Запускаем базовое приложение: сенсоры, HTTP, автоматические задачи и т.д.
    app.begin();
    delay(3000);
    app.checkRelayStates(); // TODO: после полного перехода на MQTT синхронизировать с серверным state.
}

void loop() {
    // === Контроль Wi-Fi ===
    ensureWifiConnected();

    // === Автоостановка полива по таймеру ===
    // Это страховка от затопления: если сервер забудет прислать pump.stop, мы всё равно выключим насос.
    if (g_isWatering && g_wateringDurationSec > 0) {
        const unsigned long elapsedMs = millis() - g_wateringStartMillis;
        const unsigned long plannedMs = static_cast<unsigned long>(g_wateringDurationSec) * 1000UL;
        if (elapsedMs >= plannedMs) {
            Serial.println(F("Полив завершён по таймеру duration_s, выключаем насос (будущий шаг 5: публиковать state/ACK)."));
            stopManualWatering(String("auto-timeout"));
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
                Serial.println(F("MQTT переподключение прошло успешно, подписка восстановлена."));
                lastMqttReconnectAttempt = 0;
            }
        }
    }

    // TODO: Шаг 3 — отправлять ACK (accepted/rejected) через MQTT.
    // TODO: Шаг 4 — публиковать актуальный state (running/idle) в брокер.

    app.update();

    // Небольшая пауза, чтобы не жечь CPU впустую.
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
        Serial.println(F("Игнорируем pump.start: насос уже включен вручную, ждём завершения текущей сессии."));
        return;
    }
    if (durationSec == 0) {
        Serial.println(F("Получена pump.start без duration_s или с нулевым значением — команду игнорируем."));
        return;
    }

    // TODO: уточнить инверсию реле. Сейчас считаем, что app.setManualPumpState(true) реально включает насос.
    app.setManualPumpState(true);

    g_isWatering = true;
    g_wateringDurationSec = durationSec;
    g_wateringStartMillis = millis();
    g_activeCorrelationId = correlationId;

    Serial.print(F("Запускаем ручной полив на "));
    Serial.print(durationSec);
    Serial.print(F(" секунд, correlation_id="));
    Serial.println(correlationId);
}

static void stopManualWatering(const String& correlationId) {
    if (!g_isWatering) {
        Serial.println(F("Получили pump.stop, но полив уже остановлен. На всякий случай обнуляем состояние и выключаем насос."));
    }

    app.setManualPumpState(false);

    g_isWatering = false;
    g_wateringDurationSec = 0;
    g_wateringStartMillis = 0;
    g_activeCorrelationId = "";

    Serial.print(F("Полив остановлен. Источник остановки: "));
    Serial.println(correlationId.length() ? correlationId : String("не указан (pump.stop без correlation_id)"));
}

static void mqttCallback(char* topic, byte* payload, unsigned int length) {
    // Это входная точка всех команд ручного полива. Через неё фронтенд -> сервер -> MQTT -> устройство
    // передают pump.start/pump.stop. Любая ошибка парсинга напрямую влияет на то, включится ли реальный насос.
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
        return;
    }

    const char* type = doc["type"];
    if (!type) {
        Serial.println(F("Поле type отсутствует — команда не распознана."));
        return;
    }

    const String commandType(type);
    if (commandType == "pump.start") {
        if (!doc.containsKey("duration_s")) {
            Serial.println(F("pump.start без duration_s — команду игнорируем."));
            return;
        }
        const uint32_t durationSec = doc["duration_s"];
        const char* corr = doc["correlation_id"] | "";
        Serial.print(F("Разбор pump.start: duration_s="));
        Serial.print(durationSec);
        Serial.print(F(", correlation_id="));
        Serial.println(corr);
        startManualWatering(durationSec, String(corr));
        return;
    }

    if (commandType == "pump.stop") {
        const char* corr = doc["correlation_id"] | "";
        Serial.print(F("Разбор pump.stop, correlation_id="));
        Serial.println(corr);
        stopManualWatering(String(corr));
        return;
    }

    Serial.print(F("Нераспознанная команда type="));
    Serial.println(commandType);
}

static bool mqttReconnect() {
    Serial.print(F("Пробуем подключиться к MQTT как "));
    Serial.println(DEVICE_ID);

    if (mqttClient.connect(DEVICE_ID, MQTT_USER, MQTT_PASS)) {
        Serial.println(F("MQTT соединение установлено, подписываемся на командный топик..."));
        // После каждого реконнекта брокер забывает подписки, поэтому оформляем их заново.
        if (mqttClient.subscribe(CMD_TOPIC, 1)) {
            Serial.print(F("Подписались на "));
            Serial.print(CMD_TOPIC);
            Serial.println(F(" с QoS=1."));
        } else {
            Serial.println(F("Не удалось подписаться на командный топик — попробуем снова при следующем реконнекте."));
        }
        return true;
    }

    Serial.print(F("MQTT connect вернул ошибку, состояние клиента: "));
    Serial.println(mqttClient.state());
    return false;
}
