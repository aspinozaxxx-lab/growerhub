#pragma once

// Prostoi logger dlya novoi vetki, poka tolko konsol/Serial.
namespace Logging {

class Logger {
 public:
  static void Init();
  static void Info(const char* message);
};

}  // namespace Logging