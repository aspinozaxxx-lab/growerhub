#include "core/AppRuntime.h"

#if defined(ARDUINO)
#include <Arduino.h>

static Core::AppRuntime runtime;

void setup() {
  runtime.Init();
}

void loop() {
  runtime.Tick();
  delay(10);
}
#endif
