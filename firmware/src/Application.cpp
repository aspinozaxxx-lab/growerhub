// Реализация Application: объединяет подсистемы, управляет ручным поливом, отдаёт состояния для MQTT/сервера.
// firmware/src/Application.cpp
#include "Application.h"
#include <PubSubClient.h>
#include "System/SystemClock.h"
#include "System/Dht22RebootHelper.h"

extern const char* FW_VERSION;

WateringApplication::WateringApplication() 
    : automaticWateringEnabled(true),
      automaticLightEnabled(true),
      lastStatusPrint(0),
      testStartTime(0),
      testingActuators(false),
      testPhase(0),
      manualWateringActive(false),
      manualWateringDurationSec(0),
      manualWateringStartMillis(0),
      manualActiveCorrelationId(""),
      manualStartIso8601(""),
      lastHeartbeatMillis(0),
      mqttClient(nullptr),
      systemClock(nullptr) {}

void WateringApplication::begin() {
    //Serial.begin(115200);
    //Serial.println("\n=== GrowerHub Starting ===\n");
    
    // Инициализация в правильном порядке
    settingsManager.begin();
    systemMonitor.begin();
    
    setupActuators();
    setupSensors();
    setupNetwork();
    setupTasks();

    Serial.println("\n=== GrowerHub Ready ===\n");
}

void WateringApplication::update() {
    // Основная логика
    sensorManager.update();
    actuatorManager.update(); 
    wifiManager.update();
    otaUpdater.update();
    taskScheduler.update();
    systemMonitor.update();
    
    // Статическая переменная для интервала отправки
    static unsigned long lastSend = 0;
    
    // Логирование состояний каждые 10 секунд
    static unsigned long lastStateLog = 0;
    if (millis() - lastStateLog > 10000) {
        Serial.println("Grovika States - Pump: " + String(actuatorManager.isWaterPumpRunning() ? "ON" : "OFF") + 
                      ", Light: " + String(actuatorManager.isLightOn() ? "ON" : "OFF"));
        lastStateLog = millis();
    }
    
    // Отправка данных на сервер каждые 60 секунд
    if (millis() - lastSend > 60000) {
        SoilMoistureSensor* soilSensor = sensorManager.getSoilMoistureSensor();
        DHT22Sensor* dhtSensor = sensorManager.getDHT22Sensor();
        
        if (soilSensor && dhtSensor) {
            bool pumpState = actuatorManager.isWaterPumpRunning();
            bool lightState = actuatorManager.isLightOn();
            const float soilMoisture = soilSensor->read();
            const float temperature = dhtSensor->readTemperature();
            const float humidity = dhtSensor->read();
            const bool dhtAvailable = dhtSensor->isAvailable();
            const unsigned long nowMs = millis();

            const auto rebootDecision = Dht22RebootHelper::evaluateSample(
                dht22ConsecutiveFails_,
                lastDht22RebootAtMs_,
                dhtAvailable,
                nowMs,
                DHT22_FAIL_THRESHOLD,
                DHT22_REBOOT_COOLDOWN_MS);

            if (rebootDecision == Dht22RebootDecision::ReadyToTrigger) {
                Serial.println(F("[SYS] dht22 reboot trigger: fail-threshold reached"));
                if (systemMonitor.rebootIfSafe(String("dht22_error"), String(""))) {
                    lastDht22RebootAtMs_ = nowMs;
                    dht22ConsecutiveFails_ = 0;
                } else {
                    Serial.println(F("[SYS] dht22 reboot declined: pump running"));
                }
            } else if (rebootDecision == Dht22RebootDecision::CooldownActive) {
                Serial.println(F("[SYS] dht22 reboot skipped: cooldown active"));
            }
            
            Serial.println("Sending to server - Pump: " + String(pumpState ? "ON" : "OFF") + 
                          ", Light: " + String(lightState ? "ON" : "OFF"));
            
            httpClient.sendSensorData(
                soilMoisture,
                temperature,
                humidity,
                pumpState,
                lightState
            );
        }
        
        lastSend = millis();
    }
}

void WateringApplication::updateActuatorTest() {
    if (!testingActuators) return;
    
    unsigned long currentTime = millis();
    unsigned long elapsed = currentTime - testStartTime;
    
    switch (testPhase) {
        case 0: // Насос работает 3 секунды
            if (elapsed >= 3000) {
                Serial.println("Phase 1: Pump OFF, Light ON");
                actuatorManager.setWaterPumpState(false);
                actuatorManager.setLightState(true);
                testStartTime = currentTime;
                testPhase = 1;
            }
            break;
            
        case 1: // Свет работает 2 секунды
            if (elapsed >= 2000) {
                Serial.println("Phase 2: Light OFF - TEST COMPLETE");
                actuatorManager.setLightState(false);
                testingActuators = false;
                Serial.println("=== ACTUATOR TEST FINISHED ===");
            }
            break;
    }
}

// Упрощенные методы пока
void WateringApplication::enableAutomaticWatering() {
    automaticWateringEnabled = true;
    Serial.println("Automatic watering enabled");
}

void WateringApplication::disableAutomaticWatering() {
    automaticWateringEnabled = false;
    Serial.println("Automatic watering disabled");
}

void WateringApplication::enableAutomaticLight() {
    automaticLightEnabled = true;
    Serial.println("Automatic light control enabled");
}

void WateringApplication::disableAutomaticLight() {
    automaticLightEnabled = false;
    Serial.println("Automatic light control disabled");
}

void WateringApplication::waterPlants(int durationMs) {
    Serial.println("Manual watering for " + String(durationMs) + "ms");
    actuatorManager.setWaterPumpState(true);
    delay(durationMs);
    actuatorManager.setWaterPumpState(false);
}

void WateringApplication::setManualPumpState(bool state) {
    // Выносим прямой доступ к реле насоса, чтобы MQTT-команды могли включать/выключать помпу
    // без вмешательства в остальную автоматику. Здесь не дёргаем GPIO вручную, а идём через
    // ActuatorManager, который знает про инверсию пина и применяет внутренние проверки безопасности.
    actuatorManager.setWaterPumpState(state);
}

bool WateringApplication::isManualPumpRunning() {
    // Даём наружу актуальное состояние реле. Это будет использоваться в шагах MQTT-интеграции:
    // - шаг 2: чтобы игнорировать повторные pump.start, если уже поливаем;
    // - шаг 3: для формирования корректных ACK;
    // - шаг 4+: при публикации статуса manual watering.
    return actuatorManager.isWaterPumpRunning();
}

bool WateringApplication::manualStart(uint32_t durationSec, const String& correlationId) {
    // Стартуем manual watering по команде pump.start.
    if (manualWateringActive) {
        Serial.println(F("pump.start проигнорирован: насос уже включён вручную, держим текущую сессию."));
        return false;
    }
    if (durationSec == 0) {
        Serial.println(F("Запрос pump.start с duration_s=0 — насос не включаем."));
        return false;
    }

    // TODO: уточнить инверсию реле. Сейчас считаем, что true = включить насос (через ActuatorManager).
    setManualPumpState(true);

    manualWateringActive = true;
    manualWateringDurationSec = durationSec;
    manualWateringStartMillis = millis();
    manualActiveCorrelationId = correlationId;

    // TODO: подставить реальное UTC время старта, когда появится синхронизация (NTP/RTC).
    if (systemClock && systemClock->isTimeSet()) {
        time_t utcNow = 0;
        if (systemClock->nowUtc(utcNow)) {
            manualStartIso8601 = systemClock->formatIso8601(utcNow);
        } else {
            manualStartIso8601 = "1970-01-01T00:00:00Z";
        }
    } else {
        manualStartIso8601 = "1970-01-01T00:00:00Z";
    }

    Serial.print(F("Запускаем ручной полив на "));
    Serial.print(durationSec);
    Serial.print(F(" секунд, correlation_id="));
    Serial.println(correlationId);

    return true;
}

bool WateringApplication::manualStop(const String& correlationId) {
    // Остановка manual watering по pump.stop или авто-таймауту.
    if (!manualWateringActive) {
        Serial.println(F("Получили pump.stop, но насос уже был выключен. Обнуляем состояние на всякий случай."));
    }

    setManualPumpState(false);

    manualWateringActive = false;
    manualWateringDurationSec = 0;
    manualWateringStartMillis = 0;
    manualActiveCorrelationId = "";
    manualStartIso8601 = "";

    Serial.print(F("Полив остановлен. Источник остановки: "));
    Serial.println(correlationId.length() ? correlationId : String("не указан (pump.stop без correlation_id)"));

    return true;
}

bool WateringApplication::manualLoop() {
    bool stateChanged = false;

    if (manualWateringActive && manualWateringDurationSec > 0) {
        const unsigned long elapsedMs = millis() - manualWateringStartMillis;
        const unsigned long plannedMs = static_cast<unsigned long>(manualWateringDurationSec) * 1000UL;
        if (elapsedMs >= plannedMs) {
            Serial.println(F("auto-timeout manual watering: otklyuchaem nasos posle duration_s."));
            manualStop(String("auto-timeout"));
            stateChanged = true;
        }
    }

    return stateChanged;
}

bool WateringApplication::isManualWatering() const {
    return manualWateringActive;
}

uint32_t WateringApplication::getManualWateringDurationSec() const {
    return manualWateringDurationSec;
}

const String& WateringApplication::getManualActiveCorrelationId() const {
    return manualActiveCorrelationId;
}

const String& WateringApplication::getManualWateringStartIso8601() const {
    return manualStartIso8601;
}

void WateringApplication::setMqttClient(PubSubClient* client) {
    mqttClient = client;
    otaUpdater.setAckPublisher([this](const String& payload) {
        if (!mqttClient || !mqttClient->connected()) {
            Serial.println(F("OTA: ACK propushchen (MQTT offline)."));
            return;
        }
        String topic = "gh/dev/" + settingsManager.getDeviceID() + "/state/ack";
        mqttClient->publish(topic.c_str(), payload.c_str(), false);
    });
}

void WateringApplication::setSystemClock(SystemClock* clock) {
    systemClock = clock;
    httpClient.setTimeProvider(clock);
}

void WateringApplication::statePublishNow(bool retained) {
    if (!mqttClient || !mqttClient->connected()) {
        Serial.println(F("Не удалось отправить state: MQTT не подключён, снимок не обновлён."));
        return;
    }

    const bool manualActive = manualWateringActive;
    const bool hasCorrelation = manualActiveCorrelationId.length() > 0;
    const bool hasStartIso = manualStartIso8601.length() > 0;

    const String status = manualActive ? "running" : "idle";
    const String durationField = manualActive ? String(manualWateringDurationSec) : String("null");
    const String startedField = manualActive && hasStartIso
        ? String("\"") + manualStartIso8601 + "\""
        : String("null");
    const String correlationField = manualActive && hasCorrelation
        ? String("\"") + manualActiveCorrelationId + "\""
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
    String topic = "gh/dev/" + settingsManager.getDeviceID() + "/state";
    mqttClient->publish(topic.c_str(), payload.c_str(), retained);
}

void WateringApplication::stateHeartbeatLoop(bool mqttConnected) {
    if (!mqttConnected) {
        return;
    }

    const unsigned long nowMs = millis();
    if (nowMs - lastHeartbeatMillis >= HEARTBEAT_INTERVAL_MS) {
        statePublishNow(true);
        lastHeartbeatMillis = nowMs;
    }
}

void WateringApplication::resetHeartbeatTimer() {
    lastHeartbeatMillis = millis();
}

void WateringApplication::requestReboot(const String& correlationId) {
    systemMonitor.rebootIfSafe(String("server_command"), correlationId);
}

SystemMonitor& WateringApplication::getSystemMonitor() {
    return systemMonitor;
}

void WateringApplication::testSensors() {
    Serial.println("=== TESTING SENSORS ===");
    sensorManager.printDiagnostics();
    Serial.println("======================");
}

void WateringApplication::testActuators() {
    Serial.println("=== STARTING ACTUATOR TEST ===");
    testingActuators = true;
    testStartTime = millis();
    testPhase = 0;
    
    Serial.println("Phase 0: Pump ON");
    actuatorManager.setWaterPumpState(true);
}

String WateringApplication::getFullStatus() {
    return "System Status: OK";
}

void WateringApplication::printStatus() {
    Serial.println(getFullStatus());
}

// Private methods
void WateringApplication::setupSensors() {
    Serial.println("Setting up sensors...");
    
    SoilMoistureSensor* soilSensor = new SoilMoistureSensor(34, 2800, 1300, "Soil Sensor");
    sensorManager.addSoilMoistureSensor(soilSensor);
    
    DHT22Sensor* dhtSensor = new DHT22Sensor(15, "DHT22");
    sensorManager.addDHT22Sensor(dhtSensor);
    
    sensorManager.begin();

    // ЗАДЕРЖКА для стабилизации DHT22
    Serial.println("Waiting for sensors to stabilize...");
    delay(2000);
}

void WateringApplication::checkRelayStates() {
    Serial.println("=== RELAY DIAGNOSTICS ===");
    Serial.println("GPIO 4 (Pump): " + String(digitalRead(4) ? "HIGH" : "LOW"));
    Serial.println("GPIO 5 (Light): " + String(digitalRead(5) ? "HIGH" : "LOW"));
    Serial.println("Software states - Pump: " + String(actuatorManager.isWaterPumpRunning() ? "ON" : "OFF") +
                  ", Light: " + String(actuatorManager.isLightOn() ? "ON" : "OFF"));
    Serial.println("========================");
}

void WateringApplication::setupActuators() {
    // Оба реле с invertedLogic = true
    WaterPump* waterPump = new WaterPump(4, 300000, "Water Pump"); // inverted по умолчанию true
    Relay* lightRelay = new Relay(5, true, "Grow Light"); // СЏРІРЅРѕ true
    
    actuatorManager.addWaterPump(waterPump);
    actuatorManager.addLightRelay(lightRelay);
    actuatorManager.begin();
}

void WateringApplication::setupNetwork() {
    Serial.println("Setting up network...");
    
    // Инициализация WiFi (WiFiMulti)
    wifiManager.begin(settingsManager.getDeviceID());
    // Регистрируем до 10 известных сетей
    for (int i = 0; i < settingsManager.getWiFiCount(); ++i) {
        String ssid, pass;
        if (settingsManager.getWiFiCredential(i, ssid, pass)) {
            wifiManager.addAccessPoint(ssid, pass);
        }
    }
    // Первая попытка подключения (3 секунды таймаут внутри)
    wifiManager.reconnect();
    
    otaUpdater.begin(settingsManager.getDeviceID());
    
    // ВКЛЮЧАЕМ HTTP клиент с правильными настройками
    httpClient.begin(
        settingsManager.getServerURL(),
        settingsManager.getDeviceID(),
        settingsManager.getServerCAPem()
    );
}

void WateringApplication::setupTasks() {
    Serial.println("Setting up tasks...");
    // Пока пусто - добавим позже
}

void WateringApplication::checkWatering() {
    // Пока пусто
}

void WateringApplication::checkLight() {
    // Пока пусто
}

void WateringApplication::updateServer() {
    // Пока пусто
}

void WateringApplication::printDebugInfo() {
    Serial.println("=== Debug Info ===");
    systemMonitor.printSystemInfo();
    sensorManager.printDiagnostics();
}
