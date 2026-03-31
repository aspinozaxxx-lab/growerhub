/*
 * Chto v faile: obyavleniya modulya sbora dannyh datchikov.
 * Rol v arhitekture: modules.
 * Naznachenie: publichnyi API i tipy dlya sloya modules.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include "core/Module.h"
#include "drivers/dht/Dht22Sensor.h"
#include "drivers/soil/Rj9PortScanner.h"

namespace Core {
class EventQueue;
}

namespace Services {
class MqttService;
class TimeService;
}

namespace Modules {

class SensorHubModule : public Core::Module {
 public:
  enum class SensorStatus : uint8_t {
    kOk = 0,
    kDisconnected = 1,
    kError = 2
  };

  struct DhtReading {
    // Est li aktualnoe chtenie.
    bool available;
    // Temperatura v gradusah C.
    float temperature_c;
    // Vlazhnost v procentah.
    float humidity;
    // Tekuschiy status fizicheskogo air-datchika.
    SensorStatus status;
  };

  /**
   * Init modula datchikov.
   * @param ctx Kontekst s servisami i ocheredyu sobytiy.
   */
  void Init(Core::Context& ctx) override;
  /**
   * Obrabotka sobytiy nasosa i reboot.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param event Sobytie dlya obrabotki.
   */
  void OnEvent(Core::Context& ctx, const Core::Event& event) override;
  /**
   * Periodicheskiy tick skanirovaniya i chteniya datchikov.
   * @param ctx Kontekst s zavisimostyami modula.
   * @param now_ms Tekuschee vremya v millisekundah.
   */
  void OnTick(Core::Context& ctx, uint32_t now_ms) override;

  /**
   * Vozvrashaet ukazatel na skaner portov (const).
   */
  const Drivers::Rj9PortScanner* GetScanner() const;
  /**
   * Vozvrashaet ukazatel na skaner portov.
   */
  Drivers::Rj9PortScanner* GetScanner();
  /**
   * Vozvrashaet poslednee chtenie DHT22.
   * @param out Vyhodnaya struktura s dannymi.
   */
  bool GetDhtReading(DhtReading* out) const;
  /**
   * Vozvrashaet status pochvennogo porta.
   */
  SensorStatus GetSoilPortStatus(uint8_t port) const;
  /**
   * Vozvrashaet tekst dlya serializacii statusa.
   */
  static const char* StatusToString(SensorStatus status);

#if defined(UNIT_TEST)
  /**
   * Vozvrashaet ukazatel na DHT22 dlya testov.
   */
  Drivers::Dht22Sensor* GetDhtSensor();
#endif

 private:
  static void ScanTask(Core::Context& ctx, uint32_t now_ms);
  void ScanNow(uint32_t now_ms);
  void ReadDht(uint32_t now_ms);
  void UpdateSoilStatuses(uint32_t now_ms);
  void UpdateDhtStatusOnFailure(uint32_t now_ms, Drivers::Dht22Sensor::ReadError error);
  void PublishSensorReadErrorEvent(uint32_t now_ms, const char* error_code);
  void PublishRebootEvent(uint32_t now_ms);
  void RequestReboot(uint32_t now_ms);
  void PublishServiceEvent(
      const char* event_type,
      uint32_t now_ms,
      const char* failure_id,
      const char* error_code,
      bool auto_reboot,
      uint8_t errors_count);
  bool BuildIsoTimestamp(char* out, size_t out_size) const;
  void ResetDhtErrorEpisode();

  Drivers::Rj9PortScanner scanner_;
  uint32_t last_scan_ms_ = 0;
  uint32_t boot_started_ms_ = 0;
  bool pump_blocked_ = false;
  bool rescan_pending_ = false;
  uint32_t rescan_at_ms_ = 0;
  SensorStatus soil_status_[Drivers::Rj9PortScanner::kMaxPorts]{};
  bool soil_seen_once_[Drivers::Rj9PortScanner::kMaxPorts]{};

  Drivers::Dht22Sensor dht_;
  bool dht_enabled_ = false;
  bool dht_available_ = false;
  SensorStatus dht_status_ = SensorStatus::kDisconnected;
  bool dht_seen_once_ = false;
  float dht_temp_c_ = 0.0f;
  float dht_humidity_ = 0.0f;
  uint32_t last_dht_read_ms_ = 0;
  uint8_t dht_fail_count_ = 0;
  uint32_t last_dht_reboot_ms_ = 0;
  bool dht_reboot_pending_ = false;
  bool dht_auto_reboot_ = false;
  bool dht_error_event_sent_ = false;
  uint32_t dht_failure_seq_ = 0;
  char dht_failure_id_[32]{};

  Core::EventQueue* event_queue_ = nullptr;
  Services::MqttService* mqtt_ = nullptr;
  Services::TimeService* time_ = nullptr;
  const char* device_id_ = nullptr;
};

}
