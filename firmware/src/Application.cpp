// Реализация Application: объединяет подсистемы, управляет ручным поливом, отдаёт состояния для MQTT/сервера.
// firmware/src/Application.cpp
#include "Application.h"
#include <PubSubClient.h>

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
      mqttClient(nullptr) {}

void WateringApplication::begin() {
    //Serial.begin(115200);
    //Serial.println("\n=== GrowerHub Starting ===\n");
    
    // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ РІ РїСЂР°РІРёР»СЊРЅРѕРј РїРѕСЂСЏРґРєРµ
    settingsManager.begin();
    systemMonitor.begin();
    
    setupActuators();
    setupSensors();
    setupNetwork();
    setupTasks();

    Serial.println("\n=== GrowerHub Ready ===\n");
}

void WateringApplication::update() {
    // РћСЃРЅРѕРІРЅР°СЏ Р»РѕРіРёРєР°
    sensorManager.update();
    actuatorManager.update(); 
    wifiManager.update();
    otaUpdater.update();
    taskScheduler.update();
    systemMonitor.update();
    
    // РЎС‚Р°С‚РёС‡РµСЃРєР°СЏ РїРµСЂРµРјРµРЅРЅР°СЏ РґР»СЏ РёРЅС‚РµСЂРІР°Р»Р° РѕС‚РїСЂР°РІРєРё
    static unsigned long lastSend = 0;
    
    // Р›РѕРіРёСЂРѕРІР°РЅРёРµ СЃРѕСЃС‚РѕСЏРЅРёР№ РєР°Р¶РґС‹Рµ 10 СЃРµРєСѓРЅРґ
    static unsigned long lastStateLog = 0;
    if (millis() - lastStateLog > 10000) {
        Serial.println("Grovika States - Pump: " + String(actuatorManager.isWaterPumpRunning() ? "ON" : "OFF") + 
                      ", Light: " + String(actuatorManager.isLightOn() ? "ON" : "OFF"));
        lastStateLog = millis();
    }
    
    // РћС‚РїСЂР°РІРєР° РґР°РЅРЅС‹С… РЅР° СЃРµСЂРІРµСЂ РєР°Р¶РґС‹Рµ 60 СЃРµРєСѓРЅРґ
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
        case 0: // РќР°СЃРѕСЃ СЂР°Р±РѕС‚Р°РµС‚ 3 СЃРµРєСѓРЅРґС‹
            if (elapsed >= 3000) {
                Serial.println("Phase 1: Pump OFF, Light ON");
                actuatorManager.setWaterPumpState(false);
                actuatorManager.setLightState(true);
                testStartTime = currentTime;
                testPhase = 1;
            }
            break;
            
        case 1: // РЎРІРµС‚ СЂР°Р±РѕС‚Р°РµС‚ 2 СЃРµРєСѓРЅРґС‹
            if (elapsed >= 2000) {
                Serial.println("Phase 2: Light OFF - TEST COMPLETE");
                actuatorManager.setLightState(false);
                testingActuators = false;
                Serial.println("=== ACTUATOR TEST FINISHED ===");
            }
            break;
    }
}

// РЈРїСЂРѕС‰РµРЅРЅС‹Рµ РјРµС‚РѕРґС‹ РїРѕРєР°
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
    // Р’С‹РЅРѕСЃРёРј РїСЂСЏРјРѕР№ РґРѕСЃС‚СѓРї Рє СЂРµР»Рµ РЅР°СЃРѕСЃР°, С‡С‚РѕР±С‹ MQTT-РєРѕРјР°РЅРґС‹ РјРѕРіР»Рё РІРєР»СЋС‡Р°С‚СЊ/РІС‹РєР»СЋС‡Р°С‚СЊ РїРѕРјРїСѓ
    // Р±РµР· РІРјРµС€Р°С‚РµР»СЊСЃС‚РІР° РІ РѕСЃС‚Р°Р»СЊРЅСѓСЋ Р°РІС‚РѕРјР°С‚РёРєСѓ. Р—РґРµСЃСЊ РЅРµ РґС‘СЂРіР°РµРј GPIO РІСЂСѓС‡РЅСѓСЋ, Р° РёРґС‘Рј С‡РµСЂРµР·
    // ActuatorManager, РєРѕС‚РѕСЂС‹Р№ Р·РЅР°РµС‚ РїСЂРѕ РёРЅРІРµСЂСЃРёСЋ РїРёРЅР° Рё РїСЂРёРјРµРЅСЏРµС‚ РІРЅСѓС‚СЂРµРЅРЅРёРµ РїСЂРѕРІРµСЂРєРё Р±РµР·РѕРїР°СЃРЅРѕСЃС‚Рё.
    actuatorManager.setWaterPumpState(state);
}

bool WateringApplication::isManualPumpRunning() {
    // Р”Р°С‘Рј РЅР°СЂСѓР¶Сѓ Р°РєС‚СѓР°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ СЂРµР»Рµ. Р­С‚Рѕ Р±СѓРґРµС‚ РёСЃРїРѕР»СЊР·РѕРІР°С‚СЊСЃСЏ РІ С€Р°РіР°С… MQTT-РёРЅС‚РµРіСЂР°С†РёРё:
    // - С€Р°Рі 2: С‡С‚РѕР±С‹ РёРіРЅРѕСЂРёСЂРѕРІР°С‚СЊ РїРѕРІС‚РѕСЂРЅС‹Рµ pump.start, РµСЃР»Рё СѓР¶Рµ РїРѕР»РёРІР°РµРј;
    // - С€Р°Рі 3: РґР»СЏ С„РѕСЂРјРёСЂРѕРІР°РЅРёСЏ РєРѕСЂСЂРµРєС‚РЅС‹С… ACK;
    // - С€Р°Рі 4+: РїСЂРё РїСѓР±Р»РёРєР°С†РёРё СЃС‚Р°С‚СѓСЃР° manual watering.
    return actuatorManager.isWaterPumpRunning();
}

bool WateringApplication::manualStart(uint32_t durationSec, const String& correlationId) {
    // РЎС‚Р°СЂС‚СѓРµРј manual watering РїРѕ РєРѕРјР°РЅРґРµ pump.start.
    if (manualWateringActive) {
        Serial.println(F("pump.start РїСЂРѕРёРіРЅРѕСЂРёСЂРѕРІР°РЅ: РЅР°СЃРѕСЃ СѓР¶Рµ РІРєР»СЋС‡С‘РЅ РІСЂСѓС‡РЅСѓСЋ, РґРµСЂР¶РёРј С‚РµРєСѓС‰СѓСЋ СЃРµСЃСЃРёСЋ."));
        return false;
    }
    if (durationSec == 0) {
        Serial.println(F("Р—Р°РїСЂРѕСЃ pump.start СЃ duration_s=0 вЂ” РЅР°СЃРѕСЃ РЅРµ РІРєР»СЋС‡Р°РµРј."));
        return false;
    }

    // TODO: СѓС‚РѕС‡РЅРёС‚СЊ РёРЅРІРµСЂСЃРёСЋ СЂРµР»Рµ. РЎРµР№С‡Р°СЃ СЃС‡РёС‚Р°РµРј, С‡С‚Рѕ true = РІРєР»СЋС‡РёС‚СЊ РЅР°СЃРѕСЃ (С‡РµСЂРµР· ActuatorManager).
    setManualPumpState(true);

    manualWateringActive = true;
    manualWateringDurationSec = durationSec;
    manualWateringStartMillis = millis();
    manualActiveCorrelationId = correlationId;

    // TODO: РїРѕРґСЃС‚Р°РІРёС‚СЊ СЂРµР°Р»СЊРЅРѕРµ UTC РІСЂРµРјСЏ СЃС‚Р°СЂС‚Р°, РєРѕРіРґР° РїРѕСЏРІРёС‚СЃСЏ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ (NTP/RTC).
    manualStartIso8601 = "1970-01-01T00:00:00Z";

    Serial.print(F("Р—Р°РїСѓСЃРєР°РµРј СЂСѓС‡РЅРѕР№ РїРѕР»РёРІ РЅР° "));
    Serial.print(durationSec);
    Serial.print(F(" СЃРµРєСѓРЅРґ, correlation_id="));
    Serial.println(correlationId);

    return true;
}

bool WateringApplication::manualStop(const String& correlationId) {
    // РћСЃС‚Р°РЅРѕРІРєР° manual watering РїРѕ pump.stop РёР»Рё Р°РІС‚Рѕ-С‚Р°Р№РјР°СѓС‚Сѓ.
    if (!manualWateringActive) {
        Serial.println(F("РџРѕР»СѓС‡РёР»Рё pump.stop, РЅРѕ РЅР°СЃРѕСЃ СѓР¶Рµ Р±С‹Р» РІС‹РєР»СЋС‡РµРЅ. РћР±РЅСѓР»СЏРµРј СЃРѕСЃС‚РѕСЏРЅРёРµ РЅР° РІСЃСЏРєРёР№ СЃР»СѓС‡Р°Р№."));
    }

    setManualPumpState(false);

    manualWateringActive = false;
    manualWateringDurationSec = 0;
    manualWateringStartMillis = 0;
    manualActiveCorrelationId = "";
    manualStartIso8601 = "";

    Serial.print(F("РџРѕР»РёРІ РѕСЃС‚Р°РЅРѕРІР»РµРЅ. РСЃС‚РѕС‡РЅРёРє РѕСЃС‚Р°РЅРѕРІРєРё: "));
    Serial.println(correlationId.length() ? correlationId : String("РЅРµ СѓРєР°Р·Р°РЅ (pump.stop Р±РµР· correlation_id)"));

    return true;
}

bool WateringApplication::manualLoop() {
    // РќР°Р±Р»СЋРґР°РµРј Р·Р° РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊСЋ manual watering Рё РІС‹РєР»СЋС‡Р°РµРј РЅР°СЃРѕСЃ РїРѕ С‚Р°Р№РјР°СѓС‚Сѓ.
    if (!manualWateringActive || manualWateringDurationSec == 0) {
        return false;
    }

    const unsigned long elapsedMs = millis() - manualWateringStartMillis;
    const unsigned long plannedMs = static_cast<unsigned long>(manualWateringDurationSec) * 1000UL;
    if (elapsedMs < plannedMs) {
        return false;
    }

    Serial.println(F("РџРѕР»РёРІ Р·Р°РІРµСЂС€РёР»СЃСЏ РїРѕ duration_s, РІС‹РєР»СЋС‡Р°РµРј РЅР°СЃРѕСЃ (auto-timeout)."));
    manualStop(String("auto-timeout"));
    // TODO: РїСЂРѕР°РЅР°Р»РёР·РёСЂРѕРІР°С‚СЊ РїРѕРІС‚РѕСЂРЅС‹Рµ РїСѓР±Р»РёРєР°С†РёРё ACK/state РїСЂРё Р°РІС‚РѕРѕСЃС‚Р°РЅРѕРІРєРµ, РґРѕРіРѕРІРѕСЂРёС‚СЊСЃСЏ СЃРѕ СЃС‚РѕСЂРѕРЅРѕР№ СЃРµСЂРІРµСЂР°.
    return true;
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
}

void WateringApplication::statePublishNow(bool retained) {
    if (!mqttClient || !mqttClient->connected()) {
        Serial.println(F("РќРµ СѓРґР°Р»РѕСЃСЊ РѕС‚РїСЂР°РІРёС‚СЊ state: MQTT РЅРµ РїРѕРґРєР»СЋС‡С‘РЅ, СЃРЅРёРјРѕРє РЅРµ РѕР±РЅРѕРІР»С‘РЅ."));
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

    Serial.print(F("РћС‚РїСЂР°РІР»СЏРµРј state (retained) РІ Р±СЂРѕРєРµСЂ: "));
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

    // Р—РђР”Р•Р Р–РљРђ РґР»СЏ СЃС‚Р°Р±РёР»РёР·Р°С†РёРё DHT22
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
    // РћР±Р° СЂРµР»Рµ СЃ invertedLogic = true
    WaterPump* waterPump = new WaterPump(4, 300000, "Water Pump"); // inverted РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ true
    Relay* lightRelay = new Relay(5, true, "Grow Light"); // СЏРІРЅРѕ true
    
    actuatorManager.addWaterPump(waterPump);
    actuatorManager.addLightRelay(lightRelay);
    actuatorManager.begin();
}

void WateringApplication::setupNetwork() {
    Serial.println("Setting up network...");
    
    // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ WiFi (WiFiMulti)
    wifiManager.begin(settingsManager.getDeviceID());
    // Р РµРіРёСЃС‚СЂРёСЂСѓРµРј РґРѕ 10 РёР·РІРµСЃС‚РЅС‹С… СЃРµС‚РµР№
    for (int i = 0; i < settingsManager.getWiFiCount(); ++i) {
        String ssid, pass;
        if (settingsManager.getWiFiCredential(i, ssid, pass)) {
            wifiManager.addAccessPoint(ssid, pass);
        }
    }
    // РџРµСЂРІР°СЏ РїРѕРїС‹С‚РєР° РїРѕРґРєР»СЋС‡РµРЅРёСЏ (3 СЃРµРєСѓРЅРґС‹ С‚Р°Р№РјР°СѓС‚ РІРЅСѓС‚СЂРё)
    wifiManager.reconnect();
    
    otaUpdater.begin(settingsManager.getDeviceID());
    
    // Р’РљР›Р®Р§РђР•Рњ HTTP РєР»РёРµРЅС‚ СЃ РїСЂР°РІРёР»СЊРЅС‹РјРё РЅР°СЃС‚СЂРѕР№РєР°РјРё
    httpClient.begin(
        settingsManager.getServerURL(),
        settingsManager.getDeviceID(),
        settingsManager.getServerCAPem()
    );
}

void WateringApplication::setupTasks() {
    Serial.println("Setting up tasks...");
    // РџРѕРєР° РїСѓСЃС‚Рѕ - РґРѕР±Р°РІРёРј РїРѕР·Р¶Рµ
}

void WateringApplication::checkWatering() {
    // РџРѕРєР° РїСѓСЃС‚Рѕ
}

void WateringApplication::checkLight() {
    // РџРѕРєР° РїСѓСЃС‚Рѕ
}

void WateringApplication::updateServer() {
    // РџРѕРєР° РїСѓСЃС‚Рѕ
}

void WateringApplication::printDebugInfo() {
    Serial.println("=== Debug Info ===");
    systemMonitor.printSystemInfo();
    sensorManager.printDiagnostics();
}
