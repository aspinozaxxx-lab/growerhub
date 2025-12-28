/*
 * Chto v faile: obyavleniya formirovaniya device_id po MAC.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdint>

namespace Services {

class ChipIdProvider {
 public:
  /**
   * Virtualnyi destruktor dlya provaidera chip ID.
   */
  virtual ~ChipIdProvider() = default;
  /**
   * Chitaet MAC-adres v bufer.
   * @param mac Vyhodnoy bufer iz 6 bayt.
   */
  virtual bool ReadMac(uint8_t mac[6]) = 0;
};

class DeviceIdentity {
 public:
  /**
   * Sozdaet pustoy identifikator ustroistva.
   */
  DeviceIdentity() = default;
  /**
   * Initsializiruet device_id cherez provider MAC.
   * @param provider Provider dlya chteniya MAC.
   */
  bool Init(ChipIdProvider& provider);
  /**
   * Vozvrashaet stroku device_id.
   */
  const char* GetDeviceId() const;
  /**
   * Stroit device_id iz MAC-adresa.
   * @param mac MAC-adres iz 6 bayt.
   * @param out Vyhodnoy bufer dlya stroki ID.
   * @param out_size Razmer bufera v baytah.
   */
  static bool BuildDeviceId(const uint8_t mac[6], char* out, size_t out_size);

 private:
  char device_id_[16] = {};
};

#if defined(ARDUINO)
class EfuseMacProvider : public ChipIdProvider {
 public:
  /**
   * Chitaet MAC-adres iz eFuse.
   * @param mac Vyhodnoy bufer iz 6 bayt.
   */
  bool ReadMac(uint8_t mac[6]) override;
};
#endif

}
