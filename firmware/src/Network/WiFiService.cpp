#include "WiFiService.h"

WiFiService::WiFiService(const IWiFiSettings& settingsSource)
    : settings(settingsSource),
      asyncReconnectEnabled(false),
      networksConfigured(false),
      online(false),
      loggedNoNetworks(false),
      nextAttemptMillis(0),
      attemptCounter(0),
      lastStatus(WL_IDLE_STATUS),
      configuredNetworkCount(0) {}

bool WiFiService::connectSync() {
    configureNetworks();

    const unsigned long now = millis();
    wl_status_t status = WiFi.status();

    if (status == WL_CONNECTED) {
        if (lastStatus != WL_CONNECTED) {
            Serial.print(F("Wi-Fi connected, IP: "));
            Serial.print(WiFi.localIP());
            Serial.print(F(" Wi-Fi AP: "));
            Serial.println(WiFi.SSID());
        }
        attemptCounter = 0;
        nextAttemptMillis = now + WIFI_RETRY_INTERVAL_MS;
        lastStatus = WL_CONNECTED;
        online = true;
        return true;
    }

    if (configuredNetworkCount == 0) {
        loggedNoNetworks = true;
        nextAttemptMillis = now + WIFI_BACKOFF_INTERVAL_MS;
        online = false;
        lastStatus = status;
        return false;
    }

    Serial.println(F("WiFiMulti attempting connection..."));
    wl_status_t result = static_cast<wl_status_t>(wifiMulti.run());
    if (result == WL_CONNECTED) {
        Serial.print(F("Wi-Fi connected, IP: "));
        Serial.print(WiFi.localIP());
        Serial.print(F(" Wi-Fi AP: "));
        Serial.println(WiFi.SSID());
        attemptCounter = 0;
        nextAttemptMillis = now + WIFI_RETRY_INTERVAL_MS;
        lastStatus = WL_CONNECTED;
        online = true;
        return true;
    }

    attemptCounter = 1;
    if (attemptCounter >= static_cast<uint16_t>(configuredNetworkCount)) {
        Serial.println(F("Failed to connect to all Wi-Fi networks, backing off."));
        attemptCounter = 0;
        nextAttemptMillis = now + WIFI_BACKOFF_INTERVAL_MS;
    } else {
        nextAttemptMillis = now + WIFI_RETRY_INTERVAL_MS;
    }

    lastStatus = result;
    online = false;
    return false;
}

void WiFiService::startAsyncReconnectIfNeeded() {
    configureNetworks();
    asyncReconnectEnabled = true;
}

void WiFiService::loop(unsigned long nowMillis) {
    if (!asyncReconnectEnabled) {
        return;
    }

    configureNetworks();

    const wl_status_t status = WiFi.status();
    if (status == WL_CONNECTED) {
        if (lastStatus != WL_CONNECTED) {
            Serial.print(F("Wi-Fi connected, IP: "));
            Serial.print(WiFi.localIP());
            Serial.print(F(" Wi-Fi AP: "));
            Serial.println(WiFi.SSID());
        }
        attemptCounter = 0;
        nextAttemptMillis = nowMillis + WIFI_RETRY_INTERVAL_MS;
        lastStatus = WL_CONNECTED;
        online = true;
        return;
    }

    if (lastStatus == WL_CONNECTED) {
        Serial.println(F("Wi-Fi connection lost, will retry via WiFiMulti."));
    }
    lastStatus = status;
    online = false;

    if (nowMillis < nextAttemptMillis) {
        return;
    }

    if (configuredNetworkCount == 0) {
        if (!loggedNoNetworks) {
            Serial.println(F("No Wi-Fi networks available for WiFiMulti, backing off."));
            loggedNoNetworks = true;
        }
        nextAttemptMillis = nowMillis + WIFI_BACKOFF_INTERVAL_MS;
        return;
    }
    loggedNoNetworks = false;

    Serial.println(F("WiFiMulti attempting connection..."));
    wl_status_t result = static_cast<wl_status_t>(wifiMulti.run());
    if (result == WL_CONNECTED) {
        Serial.print(F("Wi-Fi connected, IP: "));
        Serial.print(WiFi.localIP());
        Serial.print(F(" Wi-Fi AP: "));
        Serial.println(WiFi.SSID());
        attemptCounter = 0;
        nextAttemptMillis = nowMillis + WIFI_RETRY_INTERVAL_MS;
        lastStatus = WL_CONNECTED;
        online = true;
        return;
    }

    attemptCounter++;
    if (attemptCounter >= static_cast<uint16_t>(configuredNetworkCount)) {
        Serial.println(F("Failed to connect to all Wi-Fi networks, backing off."));
        attemptCounter = 0;
        nextAttemptMillis = nowMillis + WIFI_BACKOFF_INTERVAL_MS;
    } else {
        nextAttemptMillis = nowMillis + WIFI_RETRY_INTERVAL_MS;
    }
}

bool WiFiService::isOnline() const {
    return online;
}

bool WiFiService::canTransmit() const {
    return online;
}

void WiFiService::configureNetworks() {
    if (networksConfigured) {
        return;
    }

    WiFi.setAutoReconnect(true);

    configuredNetworkCount = settings.getWiFiCount();
    if (configuredNetworkCount == 0) {
        Serial.println(F("No Wi-Fi networks configured (user or defaults)."));
        loggedNoNetworks = true;
        networksConfigured = true;
        return;
    }

    for (std::size_t i = 0; i < configuredNetworkCount; ++i) {
        const char* ssid = nullptr;
        const char* password = nullptr;
        if (!settings.getWiFiCredential(i, ssid, password)) {
            continue;
        }
        if (!ssid || ssid[0] == '\0') {
            continue;
        }
        wifiMulti.addAP(ssid, password ? password : "");
        Serial.print(F("Configured Wi-Fi network: "));
        Serial.println(ssid);
    }

    loggedNoNetworks = false;
    networksConfigured = true;
}
