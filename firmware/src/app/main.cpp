/*
 * Chto v faile: tochka vhoda Arduino i zapusk AppRuntime.
 * Rol v arhitekture: app.
 * Naznachenie: start init i osnovnogo cikla obrabotki.
 * Soderzhit: funkcii setup() i loop().
 */

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
