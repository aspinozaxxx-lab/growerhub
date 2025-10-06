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
    
public:
    WateringApplication();
    
    void begin();
    void update();

    void factoryReset();

    // Управление системой
    void enableAutomaticWatering();
    void disableAutomaticWatering();
    void enableAutomaticLight();
    void disableAutomaticLight();
    
    // Ручное управление
    void waterPlants(int durationMs = 30000);
    void toggleLight();
    void toggleWaterPump();
    
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