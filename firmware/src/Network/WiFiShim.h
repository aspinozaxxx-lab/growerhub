#pragma once

#if defined(UNIT_TEST) || defined(PIO_UNIT_TESTING)

#include "Arduino.h"
#include <cstdint>
#include <utility>
#include <vector>

#ifndef F
#define F(str) str
#endif

#ifndef WIFI_STA
#define WIFI_STA 1
#endif

enum wl_status_t : uint8_t {
    WL_IDLE_STATUS = 0,
    WL_NO_SSID_AVAIL = 1,
    WL_SCAN_COMPLETED = 2,
    WL_CONNECTED = 3,
    WL_CONNECT_FAILED = 4,
    WL_CONNECTION_LOST = 5,
    WL_DISCONNECTED = 6
};

class FakeIPAddress {
public:
    FakeIPAddress() : value("0.0.0.0") {}
    explicit FakeIPAddress(String v) : value(std::move(v)) {}
    String toString() const { return value; }

private:
    String value;
};

using IPAddress = FakeIPAddress;

class FakeWiFiClass {
public:
    FakeWiFiClass();

    void reset();

    int mode(int newMode);
    void setHostname(const char* hostname);
    void setAutoReconnect(bool enable);
    wl_status_t status() const;
    IPAddress localIP() const;
    String SSID() const;
    String SSID(int index) const;
    int RSSI(int index) const;
    int scanNetworks(bool async = false);
    int scanComplete() const;
    void scanDelete();
    void begin(const char* ssid, const char* password);

    void setStatus(wl_status_t status);
    void setConnectedSSID(const String& ssid);
    void setIPAddress(const String& ip);

private:
    wl_status_t currentStatus;
    String connectedSSID;
    String ipAddress;
};

extern FakeWiFiClass WiFi;

class FakeWiFiMulti {
public:
    FakeWiFiMulti();

    void addAP(const char* ssid, const char* password);
    wl_status_t run(uint32_t timeout = 0);

    static void setRunResults(const std::vector<wl_status_t>& results);
    static void resetResults();
    static std::size_t getRunCallCount();

private:
    std::vector<std::pair<String, String>> knownAPs;

    static std::vector<wl_status_t> runResults;
    static std::size_t runResultIndex;
    static std::size_t runCallCount;
};

using WiFiMulti = FakeWiFiMulti;

 #ifndef FAKE_SERIAL_DEFINED
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
#endif

namespace FakeWiFiShim {
    void reset();
    void setInitialStatus(wl_status_t status);
    void setNetworkIdentity(const String& ssid, const String& ip);
    void setConnectionResultSequence(const std::vector<wl_status_t>& results);
    void setMillis(unsigned long value);
    unsigned long getMillis();
    std::size_t getRunCallCount();
}

unsigned long millis();

#else

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiMulti.h>

#endif
