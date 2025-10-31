#include "Network/WiFiShim.h"

#if defined(UNIT_TEST) || defined(PIO_UNIT_TESTING)

#include <algorithm>

FakeWiFiClass WiFi;
FakeSerialClass Serial;

std::vector<wl_status_t> FakeWiFiMulti::runResults;
std::size_t FakeWiFiMulti::runResultIndex = 0;
std::size_t FakeWiFiMulti::runCallCount = 0;

namespace {
unsigned long fakeMillisValue = 0;
}

unsigned long millis() {
    return fakeMillisValue;
}

FakeWiFiClass::FakeWiFiClass() {
    reset();
}

void FakeWiFiClass::reset() {
    currentStatus = WL_IDLE_STATUS;
    connectedSSID.clear();
    ipAddress = "0.0.0.0";
}

int FakeWiFiClass::mode(int) {
    return 0;
}

void FakeWiFiClass::setHostname(const char*) {}

void FakeWiFiClass::setAutoReconnect(bool) {}

wl_status_t FakeWiFiClass::status() const {
    return currentStatus;
}

IPAddress FakeWiFiClass::localIP() const {
    return IPAddress(ipAddress);
}

String FakeWiFiClass::SSID() const {
    return connectedSSID;
}

String FakeWiFiClass::SSID(int) const {
    return connectedSSID;
}

int FakeWiFiClass::RSSI(int) const {
    return -60;
}

int FakeWiFiClass::scanNetworks(bool) {
    return 0;
}

int FakeWiFiClass::scanComplete() const {
    return 0;
}

void FakeWiFiClass::scanDelete() {}

void FakeWiFiClass::begin(const char*, const char*) {
    currentStatus = WL_DISCONNECTED;
}

void FakeWiFiClass::setStatus(wl_status_t status) {
    currentStatus = status;
}

void FakeWiFiClass::setConnectedSSID(const String& ssid) {
    connectedSSID = ssid;
}

void FakeWiFiClass::setIPAddress(const String& ip) {
    ipAddress = ip;
}

FakeWiFiMulti::FakeWiFiMulti() = default;

void FakeWiFiMulti::addAP(const char* ssid, const char* password) {
    knownAPs.emplace_back(String(ssid ? ssid : ""), String(password ? password : ""));
}

wl_status_t FakeWiFiMulti::run(uint32_t) {
    ++runCallCount;
    wl_status_t result = (runResultIndex < runResults.size())
        ? runResults[runResultIndex++]
        : WiFi.status();

    WiFi.setStatus(result);
    if (result == WL_CONNECTED && WiFi.SSID().empty() && !knownAPs.empty()) {
        WiFi.setConnectedSSID(knownAPs.front().first);
    }
    return result;
}

void FakeWiFiMulti::setRunResults(const std::vector<wl_status_t>& results) {
    runResults = results;
    runResultIndex = 0;
    runCallCount = 0;
}

void FakeWiFiMulti::resetResults() {
    runResults.clear();
    runResultIndex = 0;
    runCallCount = 0;
}

std::size_t FakeWiFiMulti::getRunCallCount() {
    return runCallCount;
}

namespace FakeWiFiShim {
    void reset() {
        WiFi.reset();
        FakeWiFiMulti::resetResults();
        setMillis(0);
    }

    void setInitialStatus(wl_status_t status) {
        WiFi.setStatus(status);
    }

    void setNetworkIdentity(const String& ssid, const String& ip) {
        WiFi.setConnectedSSID(ssid);
        WiFi.setIPAddress(ip);
    }

    void setConnectionResultSequence(const std::vector<wl_status_t>& results) {
        FakeWiFiMulti::setRunResults(results);
    }

    void setMillis(unsigned long value) {
        fakeMillisValue = value;
    }

    unsigned long getMillis() {
        return fakeMillisValue;
    }

    std::size_t getRunCallCount() {
        return FakeWiFiMulti::getRunCallCount();
    }
}

#endif

