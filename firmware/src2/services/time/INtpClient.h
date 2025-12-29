/*
 * Chto v faile: obyavlenie interfeisa klienta NTP.
 * Rol v arhitekture: services/time.
 * Naznachenie: abstrakciya dlya sinhronizacii UTC po seti.
 * Soderzhit: interfeis i bazovye metody sinhronizacii.
 */

#pragma once

#include <cstdint>
#include <ctime>

namespace Services {

class INtpClient {
 public:
  // Destruktor interfeisa NTP.
  virtual ~INtpClient() = default;
  // Inicializaciya klienta NTP.
  virtual bool Begin() = 0;
  // Odin sinhronnyy zapros NTP s tajmautom.
  virtual bool SyncOnce(uint32_t timeout_ms) = 0;
  // Vozvrashaet poslednee poluchennoe UTC vremya.
  virtual bool GetTime(std::time_t& out_utc) const = 0;
  // Priznak aktivnoy sinhronizacii.
  virtual bool IsSyncInProgress() const = 0;
};

} // namespace Services


