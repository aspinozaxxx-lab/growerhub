/*
 * Chto v faile: adaptter NTP dlya ESP32 na baze configTime/gettimeofday.
 * Rol v arhitekture: services/time.
 * Naznachenie: realnyy NTP-klient dlya ESP32.
 * Soderzhit: realizaciyu zaprosa NTP i kesh vremeni.
 */

#pragma once

#include <cstdint>
#include <ctime>

#include "services/time/INtpClient.h"

namespace Services {

class ESP32NtpClientAdapter : public INtpClient {
 public:
  // Konstruktor s neobyazatelnym serverom.
  explicit ESP32NtpClientAdapter(const char* server = nullptr);
  // Inicializaciya vnutrennego sostoyaniya.
  bool Begin() override;
  // Odin sinhronnyy zapros NTP s tajmautom.
  bool SyncOnce(uint32_t timeout_ms) override;
  // Vozvrashaet poslednee poluchennoe UTC vremya.
  bool GetTime(std::time_t& out_utc) const override;
  // Priznak aktivnoy sinhronizacii.
  bool IsSyncInProgress() const override;

 private:
  // Vybor NTP servera po umolchaniyu.
  const char* ResolveServer(const char* candidate) const;

  const char* server_;
  std::time_t last_sync_utc_;
  bool has_fresh_;
  bool in_progress_;
};

} // namespace Services


