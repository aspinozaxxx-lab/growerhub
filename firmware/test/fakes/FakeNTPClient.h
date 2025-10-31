#pragma once

#include <queue>
#include "System/Time/INTPClient.h"

// Фейковый NTP клиент: позволяет задавать последовательность syncOnce()/getTime().
class FakeNTPClient : public INTPClient {
public:
    FakeNTPClient();

    bool begin() override;
    bool syncOnce() override;
    bool getTime(time_t& outUtc) const override;
    bool isSyncInProgress() const override;

    void enqueueResult(bool success, time_t utcValue); // success=false → ошибка, иначе выдаём utcValue.
    void clear();
    unsigned callCount() const;

private:
    mutable std::queue<std::pair<bool, time_t>> results;
    mutable bool lastSuccess;
    mutable time_t lastTime;
    unsigned syncCalls;
};

