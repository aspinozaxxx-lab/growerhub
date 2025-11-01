#pragma once

#include <ctime>
#include <cstdint>
#include "System/Time/INTPClient.h"

// Adapter NTP dlia ESP32 na baze configTime/gettimeofday.
class ESP32NTPClientAdapter : public INTPClient {
public:
    explicit ESP32NTPClientAdapter(const char* server = nullptr);

    bool begin() override;                          // Podgotovka vnutrennih sostoyanii.
    bool syncOnce() override;                       // Sinhronizacia s serverom NTP s tajmautom po umolchaniyu.
    bool getTime(time_t& outUtc) const override;    // Vozvrashaet poslednee validnoe vremia.
    bool isSyncInProgress() const override;         // Priznak tekuschei sinhronizacii.

    bool syncOnce(uint32_t timeoutMs);              // Ruchnoi vyzov s ukazaniem tajmauta.

private:
    const char* resolveServer(const char* candidate) const;

    const char* server_;
    time_t lastSyncUtc_;
    bool hasFresh_;
    bool inProgress_;
};
