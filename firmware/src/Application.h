// Модуль Application — координирует датчики, актуаторы и ручной полив через высокоуровневые методы.
// firmware/src/Application.h
#pragma once
#include <Arduino.h>

class PubSubClient;

// Р’РєР»СЋС‡Р°РµРј РІСЃРµ РЅРµРѕР±С…РѕРґРёРјС‹Рµ Р·Р°РіРѕР»РѕРІРєРё
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
    // РњРµРЅРµРґР¶РµСЂС‹
    SensorManager sensorManager;
    ActuatorManager actuatorManager;
    WiFiManager wifiManager;
    OTAUpdater otaUpdater;
    WateringHTTPClient httpClient;
    SettingsManager settingsManager;
    TaskScheduler taskScheduler;
    SystemMonitor systemMonitor;
    
    // РЎРѕСЃС‚РѕСЏРЅРёРµ
    bool automaticWateringEnabled;
    bool automaticLightEnabled;
    unsigned long lastStatusPrint;

    unsigned long testStartTime;
    bool testingActuators;
    int testPhase;

    // РЎРѕСЃС‚РѕСЏРЅРёРµ СЂСѓС‡РЅРѕРіРѕ РїРѕР»РёРІР° (РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РґР»СЏ MQTT Рё Р°РІС‚Рѕ-С‚Р°Р№РјР°СѓС‚Р°)
    bool manualWateringActive;
    uint32_t manualWateringDurationSec;
    unsigned long manualWateringStartMillis;
    String manualActiveCorrelationId;
    String manualStartIso8601;
    unsigned long lastHeartbeatMillis;
    PubSubClient* mqttClient;
    static constexpr unsigned long HEARTBEAT_INTERVAL_MS = 20000UL;
    
public:
    WateringApplication();
    
    void begin();
    void update();

    //void factoryReset();

    // РЈРїСЂР°РІР»РµРЅРёРµ СЃРёСЃС‚РµРјРѕР№
    void enableAutomaticWatering();
    void disableAutomaticWatering();
    void enableAutomaticLight();
    void disableAutomaticLight();
    
    // Р СѓС‡РЅРѕРµ СѓРїСЂР°РІР»РµРЅРёРµ
    void waterPlants(int durationMs = 30000);
    void toggleLight();
    void toggleWaterPump();
    // РџСЂСЏРјРѕР№ РєРѕРЅС‚СЂРѕР»СЊ РЅР°СЃРѕСЃР° РґР»СЏ СЃС†РµРЅР°СЂРёРµРІ СЂСѓС‡РЅРѕРіРѕ РїРѕР»РёРІР° РїРѕ MQTT (С€Р°РіРё 2+).
    void setManualPumpState(bool state);
    bool isManualPumpRunning();

    // Р СѓС‡РЅРѕР№ РїРѕР»РёРІ (РёРЅС‚РµРіСЂР°С†РёСЏ СЃ main.cpp)
    bool manualStart(uint32_t durationSec, const String& correlationId);
    bool manualStop(const String& correlationId);
    bool manualLoop();
    bool isManualWatering() const;
    uint32_t getManualWateringDurationSec() const;
    const String& getManualActiveCorrelationId() const;
    const String& getManualWateringStartIso8601() const;

    void setMqttClient(PubSubClient* client);
    void statePublishNow(bool retained = true);
    void stateHeartbeatLoop(bool mqttConnected);
    void resetHeartbeatTimer();
    
    // РўРµСЃС‚РёСЂРѕРІР°РЅРёРµ
    void testSensors();
    void testActuators();
    
    // РЎС‚Р°С‚СѓСЃ
    String getFullStatus();
    void printStatus();

    void checkRelayStates();
    
private:
    void updateActuatorTest();
    void setupSensors();    
    void setupActuators();
    void setupNetwork();
    void setupTasks();
    
    // РђРІС‚РѕРјР°С‚РёС‡РµСЃРєРёРµ Р·Р°РґР°С‡Рё
    void checkWatering();
    void checkLight();
    void updateServer();
    void printDebugInfo();
};
