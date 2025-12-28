#pragma once

#include <cstdint>

namespace Drivers {

class Rj9PortScanner {
 public:
  void Init();
  bool Scan(uint8_t* out_port);
};

}
