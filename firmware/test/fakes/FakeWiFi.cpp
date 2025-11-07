#include "Network/WiFiShim.h"
#include "FakeMillis.h"

#if defined(UNIT_TEST) || defined(PIO_UNIT_TESTING)

#include <algorithm>

namespace {
    wl_status_t g_status = WL_IDLE_STATUS;
    String g_connectedSsid;
    String g_ipAddress = "0.0.0.0";
    bool g_autoReconnect = false;
    std::vector<wl_status_t> g_resultSequence;
    std::size_t g_resultIndex = 0;
    std::size_t g_runCallCount = 0;
}

FakeWiFiClass WiFi;

FakeWiFiMulti::FakeWiFiMulti() = default;

FakeWiFiClass::FakeWiFiClass() {
    reset();
}

void FakeWiFiClass::reset() {
    g_status = WL_IDLE_STATUS;
    g_connectedSsid.clear();
    g_ipAddress = "0.0.0.0";
    g_autoReconnect = false;
}

int FakeWiFiClass::mode(int) {
    return WIFI_STA;
}

void FakeWiFiClass::setHostname(const char*) {}

void FakeWiFiClass::setAutoReconnect(bool enable) {
    g_autoReconnect = enable;
}

wl_status_t FakeWiFiClass::status() const {
    return g_status;
}

IPAddress FakeWiFiClass::localIP() const {
    return IPAddress(g_ipAddress);
}

String FakeWiFiClass::SSID() const {
    return g_connectedSsid;
}

String FakeWiFiClass::SSID(int) const {
    return g_connectedSsid;
}

int FakeWiFiClass::RSSI(int) const {
    return -55;
}

int FakeWiFiClass::scanNetworks(bool) {
    return 0;
}

int FakeWiFiClass::scanComplete() const {
    return 0;
}

void FakeWiFiClass::scanDelete() {}

void FakeWiFiClass::begin(const char*, const char*) {
    g_status = WL_DISCONNECTED;
}

void FakeWiFiClass::setStatus(wl_status_t status) {
    g_status = status;
}

void FakeWiFiClass::setConnectedSSID(const String& ssid) {
    g_connectedSsid = ssid;
}

void FakeWiFiClass::setIPAddress(const String& ip) {
    g_ipAddress = ip;
}

void FakeWiFiMulti::addAP(const char* ssid, const char* password) {
    knownAPs.emplace_back(String(ssid ? ssid : ""), String(password ? password : ""));
}

wl_status_t FakeWiFiMulti::run(uint32_t) {
    ++g_runCallCount;

    wl_status_t result;
    if (g_resultIndex < g_resultSequence.size()) {
        result = g_resultSequence[g_resultIndex++];
    } else {
        result = g_status;
    }

    if (result == WL_CONNECTED) {
        g_status = WL_CONNECTED;
        if (g_connectedSsid.empty()) {
            if (!knownAPs.empty()) {
                g_connectedSsid = knownAPs.front().first;
            }
        }
    } else {
        g_status = result;
    }

    return result;
}

void FakeWiFiMulti::setRunResults(const std::vector<wl_status_t>& results) {
    g_resultSequence = results;
    g_resultIndex = 0;
    g_runCallCount = 0;
}

void FakeWiFiMulti::resetResults() {
    g_resultSequence.clear();
    g_resultIndex = 0;
    g_runCallCount = 0;
}

std::size_t FakeWiFiMulti::getRunCallCount() {
    return g_runCallCount;
}

namespace FakeWiFiShim {
    void reset() {
        WiFi.reset();
        FakeWiFiMulti::resetResults();
        g_runCallCount = 0;
        g_resultIndex = 0;
        FakeMillis::set(0);
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
        FakeMillis::set(value);
    }

    unsigned long getMillis() {
        return FakeMillis::get();
    }

    std::size_t getRunCallCount() {
        return g_runCallCount;
    }
}

#endif
