// Реализация MQTTClient: удерживает соединение и передаёт pump-команды/ACK/state между брокером и приложением.
#include "Network/MQTTClient.h"

#include <WiFi.h>
#include "System/SettingsManager.h"

namespace Network {

MQTTClient* MQTTClient::activeInstance = nullptr;

MQTTClient::MQTTClient(SettingsManager& settingsRef, WiFiClient& wifiClientRef)
    : settings(settingsRef),
      wifiClient(wifiClientRef),
      mqttClient(wifiClientRef),
      lastReconnectAttempt(0),
      commandHandler(nullptr),
      connectedHandler(nullptr),
      pumpStatusProvider([]() { return false; }) {}

void MQTTClient::begin() {
    activeInstance = this;
    mqttClient.setCallback(MQTTClient::mqttCallbackRouter);
    lastReconnectAttempt = 0;
}

void MQTTClient::loop(bool wifiConnected) {
    if (!wifiConnected) {
        if (mqttClient.connected()) {
            mqttClient.disconnect();
        }
        lastReconnectAttempt = 0;
        return;
    }

    if (mqttClient.connected()) {
        mqttClient.loop();
        return;
    }

    unsigned long now = millis();
    if (now - lastReconnectAttempt >= MQTT_RECONNECT_INTERVAL_MS) {
        lastReconnectAttempt = now;
        Serial.println(F("MQTT not connected, retrying..."));
        if (reconnect()) {
            Serial.println(F("MQTT reconnected successfully."));
            lastReconnectAttempt = 0;
        }
    }
}

bool MQTTClient::isConnected() {
    return mqttClient.connected();
}

PubSubClient& MQTTClient::client() {
    return mqttClient;
}

void MQTTClient::setCommandHandler(CommandHandler handler) {
    commandHandler = std::move(handler);
}

void MQTTClient::setConnectedHandler(ConnectedHandler handler) {
    connectedHandler = std::move(handler);
}

void MQTTClient::setPumpStatusProvider(std::function<bool()> provider) {
    pumpStatusProvider = std::move(provider);
}

void MQTTClient::mqttCallbackRouter(char* topic, byte* payload, unsigned int length) {
    if (activeInstance) {
        activeInstance->handleMessage(topic, payload, length);
    }
}

void MQTTClient::handleMessage(char* topic, byte* payload, unsigned int length) {
    Serial.println(F("----- MQTT команда -----"));
    Serial.print(F("РўРѕРїРёРє: "));
    Serial.println(topic);

    Serial.print(F("Полезная нагрузка: "));
    for (unsigned int i = 0; i < length; ++i) {
        Serial.print(static_cast<char>(payload[i]));
    }
    Serial.println();

    Serial.print(F("Длина полезной нагрузки (байт): "));
    Serial.println(length);

    StaticJsonDocument<256> doc;
    DeserializationError err = deserializeJson(doc, payload, length);
    if (err) {
        Serial.print(F("Не удалось распарсить JSON команды: "));
        Serial.println(err.c_str());
        publishAckError(String(""), "bad command format: invalid JSON");
        return;
    }

    const char* type = doc["type"];
    String correlationId = "";
    if (doc.containsKey("correlation_id") && doc["correlation_id"].is<const char*>()) {
        correlationId = String(doc["correlation_id"].as<const char*>());
    }

    if (!type) {
        Serial.println(F("Поле type отсутствует — команда не распознана."));
        publishAckError(correlationId, "bad command format: type missing");
        return;
    }

    const String commandType(type);
    if (commandType == "pump.start") {
        if (!doc.containsKey("duration_s") ||
            !(doc["duration_s"].is<uint32_t>() || doc["duration_s"].is<unsigned long>() || doc["duration_s"].is<int>())) {
            Serial.println(F("pump.start без duration_s или с некорректным типом — игнорируем."));
            publishAckError(correlationId, "bad command format: duration_s missing or invalid");
            return;
        }

        uint32_t durationSec = doc["duration_s"];
        if (durationSec == 0) {
            Serial.println(F("pump.start с duration_s=0 — игнорируем команду."));
            publishAckError(correlationId, "bad command format: duration_s missing or invalid");
            return;
        }

        Serial.print(F("Разбор pump.start: duration_s="));
        Serial.print(durationSec);
        Serial.print(F(", correlation_id="));
        Serial.println(correlationId);

        if (commandHandler) {
            commandHandler(commandType, doc, correlationId);
        }

        if (correlationId.length() > 0) {
            publishAckAccepted(correlationId, "running");
        } else {
            Serial.println(F("pump.start без correlation_id — выполнили команду, но сообщаем серверу об ошибке формата."));
            publishAckError(String(""), "bad command format: correlation_id missing");
        }
        return;
    }

    if (commandType == "pump.stop") {
        Serial.print(F("obrabotka pump.stop, correlation_id="));
        Serial.println(correlationId);

        if (commandHandler) {
            commandHandler(commandType, doc, correlationId);
        }

        if (correlationId.length() > 0) {
            publishAckAccepted(correlationId, "idle");
        } else {
            Serial.println(F("pump.stop bez correlation_id - server ne poluchit svyazannyj ACK."));
            publishAckError(String(""), "bad command format: correlation_id missing");
        }
        return;
    }

    if (commandType == "reboot") {
        // Komanda reboot trebuet korektnogo correlation_id dlya otveta serveru.
        if (correlationId.length() == 0) {
            Serial.println(F("reboot bez correlation_id - otklonyaem komandу."));
            publishAckError(String(""), "bad-correlation-id");
            return;
        }

        Serial.print(F("obrabotka reboot, correlation_id="));
        Serial.println(correlationId);

        if (commandHandler) {
            commandHandler(commandType, doc, correlationId);
        }
        return;
    }

    Serial.println(commandType);
    publishAckError(correlationId, "unsupported command type");
}

bool MQTTClient::reconnect() {
    String clientId = settings.getDeviceID();
    String host = settings.getMqttHost();
    uint16_t port = settings.getMqttPort();
    String user = settings.getMqttUser();
    String pass = settings.getMqttPass();

    mqttClient.setServer(host.c_str(), port);

    Serial.print(F("MQTT connect as "));
    Serial.println(clientId);

    if (mqttClient.connect(clientId.c_str(), user.c_str(), pass.c_str())) {
        Serial.println(F("MQTT connected, subscribing..."));
        String cmdTopic = buildDeviceTopic("cmd");
        if (mqttClient.subscribe(cmdTopic.c_str(), 1)) {
            Serial.print(F("Subscribed to "));
            Serial.println(cmdTopic);
            if (connectedHandler) {
                connectedHandler();
            }
        } else {
            Serial.println(F("Failed to subscribe to command topic."));
        }
        return true;
    }

    Serial.print(F("MQTT connect failed, state="));
    Serial.println(mqttClient.state());
    return false;
}

String MQTTClient::buildDeviceTopic(const char* suffix) const {
    String topic = "gh/dev/";
    topic += settings.getDeviceID();
    topic += "/";
    topic += suffix;
    return topic;
}

void MQTTClient::publishAckStatus(const String& correlationId, const char* statusText, bool accepted) {
    if (!mqttClient.connected()) {
        Serial.println(F("�� 㤠���� ��ࠢ��� ACK (status): MQTT �� ��������, ᮮ�饭�� ����ﭮ."));
        return;
    }

    const String status = statusText ? String(statusText) : String("");
    const char* resultText = accepted ? "accepted" : "declined";
    const String payload =
        String("{\"correlation_id\":\"") + correlationId +
        "\",\"result\":\"" + String(resultText) + "\",\"status\":\"" + status + "\"}";

    Serial.print(F("��ࠢ�塞 ACK (status) � �ப��: "));
    Serial.println(payload);
    const String ackTopic = buildDeviceTopic("state/ack"); // server slushaet state/ack; vyrovnyali protokol
    mqttClient.publish(ackTopic.c_str(), payload.c_str(), false);
}

void MQTTClient::publishAckAccepted(const String& correlationId, const char* statusText) {
    publishAckStatus(correlationId, statusText, true);
}

void MQTTClient::publishAckError(const String& correlationId, const char* reasonText) {
    if (!mqttClient.connected()) {
        Serial.println(F("�� 㤠���� ��ࠢ��� ACK (error): MQTT �� ��������, ᮮ�饭�� ����ﭮ."));
        return;
    }

    const String reason = reasonText ? String(reasonText) : String("unknown error");
    const String payload =
        String("{\"correlation_id\":\"") + correlationId +
        "\",\"result\":\"error\",\"reason\":\"" + reason + "\"}";

    Serial.print(F("��ࠢ�塞 ACK (error) � �ப��: "));
    Serial.println(payload);
    const String ackTopic = buildDeviceTopic("state/ack"); // server slushaet state/ack; vyrovnyali protokol
    mqttClient.publish(ackTopic.c_str(), payload.c_str(), false);
}

} // namespace Network
