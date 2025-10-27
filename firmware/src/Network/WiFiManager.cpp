// firmware/src/Network/WiFiManager.cpp
#include "WiFiManager.h"

WiFiManager::WiFiManager() 
    : connected(false), lastConnectAttempt(0) {}

void WiFiManager::begin(const String& deviceHostname) {
    hostname = deviceHostname;
    WiFi.mode(WIFI_STA);
    WiFi.setHostname(hostname.c_str());
}

void WiFiManager::addAccessPoint(const String& ssid, const String& password) {
    if (ssid.length() == 0) return;
    for (int i = 0; i < knownCount; ++i) {
        if (knownAPs[i].ssid == ssid) {
            // Уже в списке — обновлять пароль в wifiMulti напрямую нельзя, поэтому просто выходим
            return;
        }
    }
    WiFi.persistent(false);
    wifiMulti.addAP(ssid.c_str(), password.c_str());
    if (knownCount < 10) {
        knownAPs[knownCount].ssid = ssid;
        knownAPs[knownCount].password = password;
        knownCount++;
    }
}

void WiFiManager::update() {
    // Update cached connection state
    connected = (WiFi.status() == WL_CONNECTED);

    // If not connected, periodically scan and attempt reconnect
    if (!connected) {
        if (scanning) {
            int sc = WiFi.scanComplete();
            if (sc >= 0) {
                handleScanComplete();
            }
        } else {
            if (millis() - lastScanTime >= SCAN_INTERVAL) {
                startAsyncScan();
            //} else if (millis() - lastConnectAttempt >= RECONNECT_INTERVAL) {
            //    reconnect();
            }
        }
    }
}

bool WiFiManager::isConnected() {
    return WiFi.status() == WL_CONNECTED;
}

String WiFiManager::getStatus() {
    if (isConnected()) {
        return "WiFi[Connected, SSID:" + getSSID() + ", IP:" + getIPAddress() + "]";
    } else {
        return "WiFi[Disconnected]";
    }
}

String WiFiManager::getIPAddress() {
    return WiFi.localIP().toString();
}

String WiFiManager::getSSID() {
    return WiFi.SSID();
}

void WiFiManager::reconnect() {
    if (isConnected()) return;
    Serial.println("Connecting via WiFiMulti (5s timeout)...");
    lastConnectAttempt = millis();
    // Some cores return uint8_t from WiFiMulti::run(); cast to wl_status_t for compatibility
    wl_status_t st = static_cast<wl_status_t>(wifiMulti.run(5000));
    if (st == WL_CONNECTED) {
        onConnected();
    } else {
        onDisconnected();
    }
}

void WiFiManager::startAsyncScan() {
    if (scanning) return;
    Serial.println("Starting async WiFi scan...");
    int res = WiFi.scanNetworks(true /* async */);
    if (res == -1) {
        scanning = true;
        lastScanTime = millis();
    } else if (res >= 0) {
        // Scan finished immediately; handle results
        scanning = true;
        handleScanComplete();
    }
}

void WiFiManager::handleScanComplete() {
    int n = WiFi.scanComplete();
    if (n >= 0) {
        // Choose the known AP with the best RSSI
        int bestIdx = -1;
        int bestRSSI = -1000;
        String bestSSID;
        String bestPASS;
        for (int i = 0; i < n; ++i) {
            String s = WiFi.SSID(i);
            int r = WiFi.RSSI(i);
            for (int k = 0; k < knownCount; ++k) {
                if (s == knownAPs[k].ssid) {
                    if (r > bestRSSI) {
                        bestRSSI = r;
                        bestSSID = knownAPs[k].ssid;
                        bestPASS = knownAPs[k].password;
                        bestIdx = i;
                    }
                }
            }
        }
        if (bestIdx >= 0) {
            Serial.println("Known AP found: " + bestSSID + ", RSSI=" + String(bestRSSI) + ". Connecting...");
            WiFi.begin(bestSSID.c_str(), bestPASS.c_str());
            lastConnectAttempt = millis();
        } else {
            Serial.println("No known APs found in scan results.");
        }
    }
    WiFi.scanDelete();
    scanning = false;
}

void WiFiManager::onConnected() {
    connected = true;
    Serial.println("\nWiFi connected!");
    Serial.println("IP address: " + getIPAddress());
}

void WiFiManager::onDisconnected() {
    connected = false;
    Serial.println("\nWiFi connection failed");
}

