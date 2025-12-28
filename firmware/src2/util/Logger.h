#pragma once

namespace Util {

class Logger {
 public:
  static void Init();
  static void Info(const char* message);
};

}
