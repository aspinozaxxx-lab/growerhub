#pragma once

#include <cstddef>
#include <cstdint>

namespace Util {

static const uint32_t kScenariosSchemaVersion = 2;
static const uint32_t kWifiSchemaVersion = 1;
static const size_t kMaxSoilSensors = 2;
static const size_t kMaxWaterScheduleEntries = 4;
static const size_t kMaxLightScheduleEntries = 4;

struct MoistureSensorConfig {
  uint8_t port;
  bool enabled;
  uint8_t threshold_percent;
  uint32_t duration_s;
};

struct WaterByMoistureConfig {
  bool enabled;
  uint32_t min_time_between_watering_s;
  MoistureSensorConfig sensors[kMaxSoilSensors];
};

struct WaterScheduleEntry {
  uint16_t start_hhmm;
  uint16_t duration_s;
  uint8_t days_mask;
};

struct WaterByScheduleConfig {
  bool enabled;
  size_t entry_count;
  WaterScheduleEntry entries[kMaxWaterScheduleEntries];
};

struct LightScheduleEntry {
  uint16_t start_hhmm;
  uint16_t end_hhmm;
  uint8_t days_mask;
};

struct LightByScheduleConfig {
  bool enabled;
  size_t entry_count;
  LightScheduleEntry entries[kMaxLightScheduleEntries];
};

struct ScenariosConfig {
  uint32_t schema_version;
  WaterByMoistureConfig water_moisture;
  WaterByScheduleConfig water_schedule;
  LightByScheduleConfig light_schedule;
};

ScenariosConfig DefaultScenariosConfig();
bool EncodeScenariosConfig(const ScenariosConfig& config, char* out, size_t out_size);
bool DecodeScenariosConfig(const char* json, ScenariosConfig* config);
bool ValidateScenariosConfig(const ScenariosConfig& config);
bool EncodeWifiConfig(const char* ssid, const char* password, char* out, size_t out_size);
bool ValidateWifiConfig(const char* json);

}
