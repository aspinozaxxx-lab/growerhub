// Модуль Application — координирует датчики, актуаторы и ручной полив через высокоуровневые методы.
// firmware/src/Application.h
#pragma once
#include <Arduino.h>

class PubSubClient;

// Включаем все необходимые заголовки
#include "Sensors/SensorManager.h"
#include "Actuators/ActuatorManager.h"
#include "Network/WiFiManager.h"
#include "Network/OTAUpdater.h"
#include "Network/HTTPClient.h"
#include "System/SettingsManager.h"
#include "System/TaskScheduler.h"
#include "System/SystemMonitor.h"

class SystemClock;

class WateringApplication {
private:
    // Менеджеры
    SensorManager sensorManager;
    ActuatorManager actuatorManager;
    WiFiManager wifiManager;
    OTAUpdater otaUpdater;
    WateringHTTPClient httpClient;
    SettingsManager settingsManager;
    TaskScheduler taskScheduler;
    SystemMonitor systemMonitor;
    
    // Состояние
    bool automaticWateringEnabled;
    bool automaticLightEnabled;
    unsigned long lastStatusPrint;

    unsigned long testStartTime;
    bool testingActuators;
    int testPhase;

    // Состояние ручного полива (используется для MQTT и авто-таймаута)
    bool manualWateringActive;
    uint32_t manualWateringDurationSec;
    unsigned long manualWateringStartMillis;
    String manualActiveCorrelationId;
    String manualStartIso8601;
    unsigned long lastHeartbeatMillis;
    PubSubClient* mqttClient;
    SystemClock* systemClock;
    static constexpr unsigned long HEARTBEAT_INTERVAL_MS = 20000UL;
    // Kol-vo podryad oshibok DHT22 dlya zapuska bezopasnogo reboot.
    static constexpr uint8_t DHT22_FAIL_THRESHOLD = 3;
    // Kuldaun mezhdu reboot po DHT22, chtoby izbezhat' petli (ms).
    static constexpr unsigned long DHT22_REBOOT_COOLDOWN_MS = 300000UL; // 5 min
    // Sostoyanie dlya monitoringa oshibok DHT22.
    uint8_t dht22ConsecutiveFails_ = 0;
    unsigned long lastDht22RebootAtMs_ = 0;
    
public:
    WateringApplication();
    
    void begin();
    void update();

    //void factoryReset();

    // Управление системой
    void enableAutomaticWatering();
    void disableAutomaticWatering();
    void enableAutomaticLight();
    void disableAutomaticLight();
    
    // Ручное управление
    void waterPlants(int durationMs = 30000);
    void toggleLight();
    void toggleWaterPump();
    // Прямой контроль насоса для сценариев ручного полива по MQTT (шаги 2+).
    void setManualPumpState(bool state);
    bool isManualPumpRunning();

    // Ручной полив (интеграция с main.cpp)
    bool manualStart(uint32_t durationSec, const String& correlationId);
    bool manualStop(const String& correlationId);
    bool manualLoop();
    bool isManualWatering() const;
    uint32_t getManualWateringDurationSec() const;
    const String& getManualActiveCorrelationId() const;
    const String& getManualWateringStartIso8601() const;

    void setMqttClient(PubSubClient* client);
    void setSystemClock(SystemClock* clock);
    void statePublishNow(bool retained = true);
    void stateHeartbeatLoop(bool mqttConnected);
    void resetHeartbeatTimer();
    void requestReboot(const String& correlationId);
    SystemMonitor& getSystemMonitor();
    
    // Тестирование
    void testSensors();
    void testActuators();
    
    // Статус
    String getFullStatus();
    void printStatus();

    void checkRelayStates();
    
private:
    void updateActuatorTest();
    void setupSensors();    
    void setupActuators();
    void setupNetwork();
    void setupTasks();
    
    // Автоматические задачи
    void checkWatering();
    void checkLight();
    void updateServer();
    void printDebugInfo();
};
