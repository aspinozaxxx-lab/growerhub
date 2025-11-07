// test/fakes/Arduino.cpp
#include "Arduino.h"
#include "FakeMillis.h"

FakeSerialClass Serial;
ESPClass ESP;

void ESPClass::restart() {}

unsigned long ESPClass::getFreeHeap() const {
    return 200000;
}

unsigned long ESPClass::getHeapSize() const {
    return 400000;
}

int ESPClass::getCpuFreqMHz() const {
    return 240;
}

size_t ESPClass::getFlashChipSize() const {
    return 4 * 1024 * 1024;
}

size_t ESPClass::getSketchSize() const {
    return 512 * 1024;
}

size_t ESPClass::getFreeSketchSpace() const {
    return 1024 * 1024;
}

void delay(unsigned long ms) {
    FakeMillis::advance(ms);
}

void yield() {}
