#include "drivers/rtc/Ds3231Driver.h"

namespace Drivers {

void Ds3231Driver::Init() {}

bool Ds3231Driver::ReadEpoch(uint32_t* out_epoch) {
  if (out_epoch == nullptr) {
    return false;
  }
  *out_epoch = 0;
  return true;
}

}
