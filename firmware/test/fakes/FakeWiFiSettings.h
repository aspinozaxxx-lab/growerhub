#pragma once

#include "System/IWiFiSettings.h"
#include <string>
#include <utility>
#include <vector>

// Простейшая реализация настроек Wi-Fi для тестов.
class FakeWiFiSettings : public IWiFiSettings {
public:
    void addCredential(const std::string& ssid, const std::string& password) {
        credentials.emplace_back(ssid, password);
    }

    void clear() {
        credentials.clear();
    }

    std::size_t getWiFiCount() const override {
        return credentials.size();
    }

    bool getWiFiCredential(std::size_t index, const char*& ssid, const char*& password) const override {
        if (index >= credentials.size()) {
            ssid = nullptr;
            password = nullptr;
            return false;
        }
        ssidStorage = credentials[index].first;
        passwordStorage = credentials[index].second;
        ssid = ssidStorage.c_str();
        password = passwordStorage.c_str();
        return true;
    }

private:
    mutable std::string ssidStorage;
    mutable std::string passwordStorage;
    std::vector<std::pair<std::string, std::string>> credentials;
};

