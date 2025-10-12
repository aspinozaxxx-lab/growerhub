#include <Arduino.h>
#include "Application.h"

WateringApplication app;

void setup() {
    app.begin();
    delay(3000);
    app.checkRelayStates(); // Добавляем эту строку11

    //app.factoryReset(); // Добавляем эту строку
    
    // ТЕСТИРОВАНИЕ - раскомментируйте что нужно:
    // delay(3000);
    // app.testSensors();
     //delay(1000);
     //app.testActuators();
}

void loop() {
    app.update();
    delay(100);
}