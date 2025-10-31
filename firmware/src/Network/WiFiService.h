#pragma once

#include "Network/WiFiShim.h"
#include <cstdint>
#include "System/IWiFiSettings.h"

// Сервис Wi-Fi с DI: переносит текущую логику подключения/ретраев в самостоятельный класс.
class WiFiService {
public:
    explicit WiFiService(const IWiFiSettings& settingsSource);

    bool connectSync();                         // Первичная синхронная попытка подключения.
    void startAsyncReconnectIfNeeded();         // Включает фоновые переподключения.
    void loop(unsigned long nowMillis);         // Поддерживает ретраи и бэкоффы по текущим правилам.

    bool isOnline() const;
    bool canTransmit() const;

private:
    void configureNetworks();

    const IWiFiSettings& settings;
    WiFiMulti wifiMulti;

    bool asyncReconnectEnabled;
    bool networksConfigured;
    bool online;
    bool loggedNoNetworks;

    unsigned long nextAttemptMillis;
    uint16_t attemptCounter;
    wl_status_t lastStatus;
    std::size_t configuredNetworkCount;

    static constexpr unsigned long WIFI_RETRY_INTERVAL_MS = 5000UL;
    static constexpr unsigned long WIFI_BACKOFF_INTERVAL_MS = 20000UL;
};
