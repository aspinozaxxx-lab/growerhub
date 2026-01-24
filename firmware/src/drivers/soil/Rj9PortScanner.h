/*
 * Chto v faile: obyavleniya skanera portov pochvennyh datchikov.
 * Rol v arhitekture: drivers.
 * Naznachenie: publichnyi API i tipy dlya sloya drivers.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdint>

namespace Drivers {

class Rj9PortScanner {
 public:
  // Maksimalnoe kolichestvo portov.
  static const size_t kMaxPorts = 2;
  // Tip callback dlya chteniya ADC.
  using AdcReader = uint16_t (*)(uint8_t pin);

  /**
   * Init skanera portov.
   * @param ports Massiv pinov ADC.
   * @param count Kolichestvo portov.
   */
  void Init(const uint8_t* ports, size_t count);
  /**
   * Ustanavlivaet callback chteniya ADC.
   * @param reader Ukazatel na funkciyu chteniya.
   */
  void SetAdcReader(AdcReader reader);
  /**
   * Ustanavlivaet kalibrovku suho/mokro.
   * @param dry_value ADC znachenie dlya suho.
   * @param wet_value ADC znachenie dlya mokro.
   */
  void SetCalibration(uint16_t dry_value, uint16_t wet_value);

  /**
   * Zapuskaet skanirovanie portov.
   */
  void Scan();
  /**
   * Proveryaet, obnaruzhen li datchik na porte.
   * @param port Nomer porta.
   */
  bool IsDetected(uint8_t port) const;
  /**
   * Vozvrashaet poslednee raw znachenie ADC.
   * @param port Nomer porta.
   */
  uint16_t GetLastRaw(uint8_t port) const;
  /**
   * Vozvrashaet poslednyy procent vlazhnosti.
   * @param port Nomer porta.
   */
  uint8_t GetLastPercent(uint8_t port) const;
  /**
   * Vozvrashaet kolichestvo aktivnyh portov.
   */
  size_t GetPortCount() const;

 private:
  uint16_t ReadAdc(uint8_t pin) const;
  uint8_t ConvertToPercent(uint16_t raw) const;

  uint8_t ports_[kMaxPorts]{};
  size_t port_count_ = 0;
  bool detected_[kMaxPorts]{};
  uint16_t last_raw_[kMaxPorts]{};
  uint8_t last_percent_[kMaxPorts]{};
  uint8_t good_count_[kMaxPorts]{};
  uint8_t bad_count_[kMaxPorts]{};
  uint16_t dry_value_ = 4095;
  uint16_t wet_value_ = 1800;
  AdcReader adc_reader_ = nullptr;
};

}
