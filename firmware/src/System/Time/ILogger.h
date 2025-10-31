#pragma once

// Лёгкий интерфейс логгера, чтобы SystemClock мог писать статусы независимо от Serial.
class ILogger {
public:
    virtual ~ILogger() = default;

    virtual void logInfo(const char* tag, const char* message) = 0;
    virtual void logWarn(const char* tag, const char* message) = 0;
    virtual void logError(const char* tag, const char* message) = 0;
};

