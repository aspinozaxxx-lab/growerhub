#pragma once

#include <ctime>

// Абстракция клиента NTP (или другого сетевого источника UTC).
class INTPClient {
public:
    virtual ~INTPClient() = default;

    virtual bool begin() = 0;                 // Первичная настройка (сервера, сокеты).
    virtual bool syncOnce() = 0;              // Принудительная попытка синхронизации.
    virtual bool getTime(time_t& outUtc) const = 0; // Последнее полученное значение UTC.
    virtual bool isSyncInProgress() const = 0; // true, если идёт активная синхронизация.
};

