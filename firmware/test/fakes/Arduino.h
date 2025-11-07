#pragma once

#include <string>
#include <ctime>
#include <cstddef>
#include <sstream>

class String : public std::string {
public:
    String() = default;
    String(const char* cstr) : std::string(cstr ? cstr : "") {}
    String(const std::string& other) : std::string(other) {}
    String(char ch) : std::string(1, ch) {}
    String(int value) : std::string(std::to_string(value)) {}
    String(unsigned int value) : std::string(std::to_string(value)) {}
    String(long value) : std::string(std::to_string(value)) {}
    String(unsigned long value) : std::string(std::to_string(value)) {}
    String(float value) : std::string(std::to_string(value)) {}
    String(double value) : std::string(std::to_string(value)) {}
    String(float value, int decimals) {
        std::ostringstream oss;
        oss.setf(std::ios::fixed);
        if (decimals >= 0) {
            oss.precision(decimals);
        }
        oss << value;
        assign(oss.str());
    }
    String(const char* data, size_t len) : std::string(data, len) {}
};

inline const char* F(const char* str) { return str; }

unsigned long millis();
void delay(unsigned long ms);
void yield();

class FakeSerialClass {
public:
    template<typename T>
    void print(const T&) {}

    template<typename T>
    void println(const T&) {}

    void println() {}
};

extern FakeSerialClass Serial;
#define FAKE_SERIAL_DEFINED

class ESPClass {
public:
    void restart();
    unsigned long getFreeHeap() const;
    unsigned long getHeapSize() const;
    int getCpuFreqMHz() const;
    size_t getFlashChipSize() const;
    size_t getSketchSize() const;
    size_t getFreeSketchSpace() const;
};

extern ESPClass ESP;

extern "C" {
struct tm* fake_gmtime(const time_t* timer);
struct tm* fake_gmtime_r(const time_t* timer, struct tm* result);
}

#define gmtime fake_gmtime
#define gmtime_r fake_gmtime_r
