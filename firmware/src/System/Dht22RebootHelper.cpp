// firmware/src/System/Dht22RebootHelper.cpp
#include "System/Dht22RebootHelper.h"

namespace Dht22RebootHelper {
namespace {
    constexpr uint8_t MAX_FAIL_COUNTER = 0xFF;
}

Dht22RebootDecision evaluateSample(uint8_t& consecutiveFails,
                                   unsigned long lastRebootAtMs,
                                   bool sensorAvailable,
                                   unsigned long nowMs,
                                   uint8_t failThreshold,
                                   unsigned long cooldownMs) {
    if (sensorAvailable) {
        consecutiveFails = 0;
        return Dht22RebootDecision::None;
    }

    if (consecutiveFails < MAX_FAIL_COUNTER) {
        ++consecutiveFails;
    }

    if (consecutiveFails < failThreshold) {
        return Dht22RebootDecision::None;
    }

    if (lastRebootAtMs == 0) {
        return Dht22RebootDecision::ReadyToTrigger;
    }

    const unsigned long elapsed = nowMs - lastRebootAtMs;
    if (elapsed >= cooldownMs) {
        return Dht22RebootDecision::ReadyToTrigger;
    }

    return Dht22RebootDecision::CooldownActive;
}

} // namespace Dht22RebootHelper
