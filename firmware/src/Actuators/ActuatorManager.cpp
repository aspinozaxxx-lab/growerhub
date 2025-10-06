#include "ActuatorManager.h"

ActuatorManager::ActuatorManager() : waterPump(nullptr), lightRelay(nullptr) {}

ActuatorManager::~ActuatorManager() {
    for (auto actuator : actuators) {
        delete actuator;
    }
}

void ActuatorManager::begin() {
    for (auto actuator : actuators) {
        actuator->begin();
    }
}

void ActuatorManager::update() {
    if (waterPump) {
        waterPump->checkSafety();
    }
}

void ActuatorManager::addWaterPump(WaterPump* pump) {
    actuators.push_back(pump);
    waterPump = pump;
}

void ActuatorManager::addLightRelay(Relay* relay) {
    actuators.push_back(relay);
    lightRelay = relay;
}

void ActuatorManager::setWaterPumpState(bool state) {
    if (waterPump) waterPump->setState(state);
}

void ActuatorManager::setLightState(bool state) {
    if (lightRelay) lightRelay->setState(state);
}

bool ActuatorManager::isWaterPumpRunning() {
    return waterPump ? waterPump->isPumpRunning() : false;
}

bool ActuatorManager::isLightOn() {
    return lightRelay ? lightRelay->getState() : false;
}

String ActuatorManager::getStatus() {
    String status = "Actuators: ";
    status += "Pump=" + String(isWaterPumpRunning() ? "ON" : "OFF");
    status += ", Light=" + String(isLightOn() ? "ON" : "OFF");
    return status;
}