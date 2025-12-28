#pragma once

#include <cstddef>
#include <cstdint>

namespace Util {

static const uint32_t kScenariosSchemaVersion = 1;
static const uint32_t kWifiSchemaVersion = 1;

struct ScenariosConfig {
  uint32_t schema_version;
  bool water_time_enabled;
  bool water_moisture_enabled;
  bool light_schedule_enabled;
};

ScenariosConfig DefaultScenariosConfig();
bool EncodeScenariosConfig(const ScenariosConfig& config, char* out, size_t out_size);
bool DecodeScenariosConfig(const char* json, ScenariosConfig* config);
bool ValidateScenariosConfig(const ScenariosConfig& config);
bool EncodeWifiConfig(const char* ssid, const char* password, char* out, size_t out_size);
bool ValidateWifiConfig(const char* json);

}
