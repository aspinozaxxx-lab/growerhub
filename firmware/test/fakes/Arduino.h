#pragma once

#include <string>
#include <ctime>

using String = std::string;

inline const char* F(const char* str) { return str; }

unsigned long millis();

extern "C" {
struct tm* fake_gmtime(const time_t* timer);
struct tm* fake_gmtime_r(const time_t* timer, struct tm* result);
}

#define gmtime fake_gmtime
#define gmtime_r fake_gmtime_r
