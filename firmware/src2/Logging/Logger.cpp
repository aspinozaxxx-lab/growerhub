#include "Logging/Logger.h"

// Perekluchenie na Serial ili printf dlya raznykh platform.
#if defined(ARDUINO)
#include <Arduino.h>
#else
#include <cstdio>
#endif

namespace Logging {

void Logger::Init() {
#if defined(ARDUINO)
  Serial.begin(115200);
#endif
}

void Logger::Info(const char* message) {
#if defined(ARDUINO)
  Serial.println(message);
#else
  std::printf("%s\n", message);
#endif
}

}  // namespace Logging