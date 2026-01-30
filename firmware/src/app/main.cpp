/*
 * Chto v faile: tochka vhoda Arduino i zapusk AppRuntime.
 * Rol v arhitekture: app.
 * Naznachenie: start init i osnovnogo cikla obrabotki.
 * Soderzhit: funkcii setup() i loop().
 */

#include "core/AppRuntime.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <esp_system.h>

static Core::AppRuntime runtime;

void setup() {
  delay(500); // chtoby monitor uspel podklyuchitsya i ne propadali pervye soobsheniya
  runtime.Init();
}

void loop() {  
  runtime.Tick();
  delay(10);
}
#endif
