#pragma once

#include <cstdint>

#include "core/Context.h"

namespace Services {

struct TimeFields {
  uint16_t year;
  uint8_t month;
  uint8_t day;
  uint8_t hour;
  uint8_t minute;
  uint8_t second;
  uint8_t wday;
};

class TimeService {
 public:
  void Init(Core::Context& ctx);
  bool GetTime(TimeFields* out) const;
  uint64_t GetUnixTimeMs() const;
  bool IsSynced() const;

#if defined(UNIT_TEST)
  void SetTimeForTests(const TimeFields& fields, uint64_t unix_ms);
  void SetSyncedForTests(bool synced);
#endif

 private:
#if defined(UNIT_TEST)
  bool test_synced_ = false;
  TimeFields test_fields_{};
  uint64_t test_unix_ms_ = 0;
#endif
};

}
