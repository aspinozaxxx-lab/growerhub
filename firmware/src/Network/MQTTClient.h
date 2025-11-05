// Обёртка над PubSubClient: подключение, подписка и маршрутизация MQTT-команд устройства.
#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <functional>
#include <PubSubClient.h>

class SettingsManager;
class WiFiClient;

namespace Network {

class MQTTClient {
public:
    using CommandHandler = std::function<void(const String& commandType, const JsonDocument& doc, const String& correlationId)>; // podderzhivaem komandy pump.start, pump.stop, reboot
    using ConnectedHandler = std::function<void()>;

    MQTTClient(SettingsManager& settings, WiFiClient& wifiClient);

    void begin();
    void loop(bool wifiConnected);
    bool isConnected();

    PubSubClient& client();

    void setCommandHandler(CommandHandler handler);
    void setConnectedHandler(ConnectedHandler handler);
    void setPumpStatusProvider(std::function<bool()> provider);

private:
    static void mqttCallbackRouter(char* topic, byte* payload, unsigned int length);
    void handleMessage(char* topic, byte* payload, unsigned int length);
    bool reconnect();

    String buildDeviceTopic(const char* suffix) const;
    void publishAckAccepted(const String& correlationId, const char* statusText);
    void publishAckError(const String& correlationId, const char* reasonText);

    SettingsManager& settings;
    WiFiClient& wifiClient;
    PubSubClient mqttClient;

    unsigned long lastReconnectAttempt;
    CommandHandler commandHandler;
    ConnectedHandler connectedHandler;
    std::function<bool()> pumpStatusProvider;

    static MQTTClient* activeInstance;
    static constexpr unsigned long MQTT_RECONNECT_INTERVAL_MS = 5000UL;
};

} // namespace Network
