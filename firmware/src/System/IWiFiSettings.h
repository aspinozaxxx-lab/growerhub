#pragma once

#include <cstddef>

// Интерфейс только для чтения Wi-Fi настроек; реализуется SettingsManager.
class IWiFiSettings {
public:
    virtual ~IWiFiSettings() = default;

    virtual std::size_t getWiFiCount() const = 0;
    virtual bool getWiFiCredential(std::size_t index, const char*& ssid, const char*& password) const = 0;
};

