#include "drivers/soil/Rj9PortScanner.h"

namespace Drivers {

void Rj9PortScanner::Init() {}

bool Rj9PortScanner::Scan(uint8_t* out_port) {
  if (out_port == nullptr) {
    return false;
  }
  *out_port = 0;
  return true;
}

}
