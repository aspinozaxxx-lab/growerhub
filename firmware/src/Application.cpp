// firmware/src/Application.cpp
#include "Application.h"

WateringApplication::WateringApplication() 
    : automaticWateringEnabled(true), automaticLightEnabled(true),
      lastStatusPrint(0) {}

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
            
            Serial.println("Sending to server - Pump: " + String(pumpState ? "ON" : "OFF") + 
                          ", Light: " + String(lightState ? "ON" : "OFF"));
            
            httpClient.sendSensorData(
                soilSensor->read(),
                dhtSensor->readTemperature(),
                dhtSensor->read(),
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
    Serial.println("GPIO 4 (Pump): " + String(digitalRead(5) ? "HIGH" : "LOW"));
    Serial.println("GPIO 5 (Light): " + String(digitalRead(4) ? "HIGH" : "LOW"));
    Serial.println("Software states - Pump: " + String(actuatorManager.isWaterPumpRunning() ? "ON" : "OFF") +
                  ", Light: " + String(actuatorManager.isLightOn() ? "ON" : "OFF"));
    Serial.println("========================");
}

void WateringApplication::setupActuators() {
    // Оба реле с invertedLogic = true
    WaterPump* waterPump = new WaterPump(5, 300000, "Water Pump"); // inverted по умолчанию true
    Relay* lightRelay = new Relay(4, true, "Grow Light"); // явно true
    
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
