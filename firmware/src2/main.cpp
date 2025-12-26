#include "Logging/Logger.h"

// Startovaya tochka dlya v2.
#if defined(ARDUINO)
#include <Arduino.h>

void setup() {
  Logging::Logger::Init();
  Logging::Logger::Info("boot v2");
}

void loop() {
  delay(1000);
}
#else
// Zapasnaya zaglushka dlya native testov.
#endif