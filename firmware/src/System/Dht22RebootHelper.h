// firmware/src/System/Dht22RebootHelper.h
#pragma once

#include <cstdint>

enum class Dht22RebootDecision {
    None,
    ReadyToTrigger,
    CooldownActive
};

namespace Dht22RebootHelper {

// Aktualiziruet sostoyanie dht22ConsecutiveFails_/lastDht22RebootAtMs_ i vozvrashchaet reshenie.
Dht22RebootDecision evaluateSample(uint8_t& consecutiveFails,
                                   unsigned long lastRebootAtMs,
                                   bool sensorAvailable,
                                   unsigned long nowMs,
                                   uint8_t failThreshold,
                                   unsigned long cooldownMs);

} // namespace Dht22RebootHelper
