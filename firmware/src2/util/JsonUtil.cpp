#include "util/JsonUtil.h"

#include <cctype>
#include <cstdio>
#include <cstring>

namespace Util {

static const char* SkipWs(const char* ptr) {
  const char* current = ptr;
  while (current && *current && std::isspace(static_cast<unsigned char>(*current))) {
    ++current;
  }
  return current;
}

static bool HasJsonBraces(const char* json) {
  if (!json) {
    return false;
  }
  const char* left = std::strchr(json, '{');
  const char* right = std::strrchr(json, '}');
  return left && right && left < right;
}

static bool ExtractUintField(const char* json, const char* key, uint32_t& out) {
  if (!json || !key) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  if (!key_pos) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value || !std::isdigit(static_cast<unsigned char>(*value))) {
    return false;
  }
  uint32_t result = 0;
  while (*value && std::isdigit(static_cast<unsigned char>(*value))) {
    result = result * 10 + static_cast<uint32_t>(*value - '0');
    ++value;
  }
  out = result;
  return true;
}

static bool ExtractBoolAfterKey(const char* json, const char* key, const char* field, bool& out) {
  if (!json || !key || !field) {
    return false;
  }
  char key_pattern[64];
  std::snprintf(key_pattern, sizeof(key_pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, key_pattern);
  if (!key_pos) {
    return false;
  }
  char field_pattern[64];
  std::snprintf(field_pattern, sizeof(field_pattern), "\"%s\"", field);
  const char* field_pos = std::strstr(key_pos + std::strlen(key_pattern), field_pattern);
  if (!field_pos) {
    return false;
  }
  const char* colon = std::strchr(field_pos + std::strlen(field_pattern), ':');
  if (!colon) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value) {
    return false;
  }
  if (std::strncmp(value, "true", 4) == 0) {
    out = true;
    return true;
  }
  if (std::strncmp(value, "false", 5) == 0) {
    out = false;
    return true;
  }
  return false;
}

ScenariosConfig DefaultScenariosConfig() {
  ScenariosConfig config{};
  config.schema_version = kScenariosSchemaVersion;
  config.water_time_enabled = false;
  config.water_moisture_enabled = false;
  config.light_schedule_enabled = false;
  return config;
}

bool EncodeScenariosConfig(const ScenariosConfig& config, char* out, size_t out_size) {
  if (!out || out_size == 0) {
    return false;
  }
  const int written = std::snprintf(
      out,
      out_size,
      "{\"schema_version\":%u,\"scenarios\":{\"water_time\":{\"enabled\":%s},\"water_moisture\":{\"enabled\":%s},\"light_schedule\":{\"enabled\":%s}}}",
      static_cast<unsigned int>(config.schema_version),
      config.water_time_enabled ? "true" : "false",
      config.water_moisture_enabled ? "true" : "false",
      config.light_schedule_enabled ? "true" : "false");
  return written > 0 && static_cast<size_t>(written) < out_size;
}

bool DecodeScenariosConfig(const char* json, ScenariosConfig* config) {
  if (!json || !config) {
    return false;
  }
  if (!HasJsonBraces(json)) {
    return false;
  }

  uint32_t schema_version = 0;
  if (!ExtractUintField(json, "schema_version", schema_version)) {
    return false;
  }

  ScenariosConfig parsed = DefaultScenariosConfig();
  parsed.schema_version = schema_version;

  bool value = false;
  if (ExtractBoolAfterKey(json, "water_time", "enabled", value)) {
    parsed.water_time_enabled = value;
  }
  if (ExtractBoolAfterKey(json, "water_moisture", "enabled", value)) {
    parsed.water_moisture_enabled = value;
  }
  if (ExtractBoolAfterKey(json, "light_schedule", "enabled", value)) {
    parsed.light_schedule_enabled = value;
  }

  *config = parsed;
  return true;
}

bool ValidateScenariosConfig(const ScenariosConfig& config) {
  return config.schema_version == kScenariosSchemaVersion;
}

}
