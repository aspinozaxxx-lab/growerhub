// firmware/src/Application.h
#pragma once
#include <Arduino.h>

// Включаем все необходимые заголовки
#include "Sensors/SensorManager.h"
#include "Actuators/ActuatorManager.h"
#include "Network/WiFiManager.h"
#include "Network/OTAUpdater.h"
#include "Network/HTTPClient.h"
#include "System/SettingsManager.h"
#include "System/TaskScheduler.h"
#include "System/SystemMonitor.h"

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
