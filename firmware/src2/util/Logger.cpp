/*
 * Chto v faile: realizaciya prostogo logirovaniya.
 * Rol v arhitekture: util.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe util.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#else
#include <cstdio>
#endif

namespace Util {

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

}
