#include "FakeNTPClient.h"

FakeNTPClient::FakeNTPClient()
    : lastSuccess(false), lastTime(0), syncCalls(0) {}

bool FakeNTPClient::begin() {
    return true;
}

bool FakeNTPClient::syncOnce() {
    ++syncCalls;
    if (results.empty()) {
        lastSuccess = false;
        return false;
    }

    auto pair = results.front();
    results.pop();
    lastSuccess = pair.first;
    lastTime = pair.second;
    return lastSuccess;
}

bool FakeNTPClient::getTime(time_t& outUtc) const {
    if (!lastSuccess) {
        return false;
    }
    outUtc = lastTime;
    return true;
}

bool FakeNTPClient::isSyncInProgress() const {
    return false;
}

void FakeNTPClient::enqueueResult(bool success, time_t utcValue) {
    results.emplace(success, utcValue);
}

void FakeNTPClient::clear() {
    std::queue<std::pair<bool, time_t>> empty;
    std::swap(results, empty);
    lastSuccess = false;
    lastTime = 0;
    syncCalls = 0;
}

unsigned FakeNTPClient::callCount() const {
    return syncCalls;
}

