#include "Relay.h"

Relay::Relay(int relayPin, bool isInverted, String relayName) 
    : pin(relayPin), invertedLogic(isInverted), 
      currentState(false), name(relayName) {}

void Relay::begin() {
    pinMode(pin, OUTPUT);
    setState(false);
}

void Relay::setState(bool state) {
    currentState = state;
    bool pinState = invertedLogic ? !state : state;
    digitalWrite(pin, pinState ? HIGH : LOW);
}

bool Relay::getState() {
    return currentState;
}

String Relay::getName() {
    return name;
}

String Relay::getStatus() {
    String stateStr = currentState ? "ON" : "OFF";
    return "Relay[pin:" + String(pin) + ", state:" + stateStr + "]";
}