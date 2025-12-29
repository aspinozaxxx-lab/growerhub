/*
 * Chto v faile: realizaciya struktur konfiguracii i JSON kodirovaniya/dekodirovaniya.
 * Rol v arhitekture: util.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe util.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "util/JsonUtil.h"

#include <cctype>
#include <cstdarg>
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

static bool ExtractUintFieldWithin(const char* start,
                                   const char* limit,
                                   const char* key,
                                   uint32_t& out) {
  if (!start || !limit || !key) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(start, pattern);
  if (!key_pos || key_pos >= limit) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon || colon >= limit) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value || value >= limit || !std::isdigit(static_cast<unsigned char>(*value))) {
    return false;
  }
  uint32_t result = 0;
  while (value < limit && *value && std::isdigit(static_cast<unsigned char>(*value))) {
    result = result * 10 + static_cast<uint32_t>(*value - '0');
    ++value;
  }
  out = result;
  return true;
}

static bool ExtractBoolWithin(const char* start,
                              const char* limit,
                              const char* key,
                              bool& out) {
  if (!start || !limit || !key) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(start, pattern);
  if (!key_pos || key_pos >= limit) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon || colon >= limit) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value || value >= limit) {
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

static bool FindObjectBoundsWithin(const char* start,
                                   const char* limit,
                                   const char* key,
                                   const char** out_start,
                                   const char** out_end) {
  if (!start || !limit || !key || !out_start || !out_end) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(start, pattern);
  if (!key_pos || key_pos >= limit) {
    return false;
  }
  const char* brace = std::strchr(key_pos + std::strlen(pattern), '{');
  if (!brace || brace >= limit) {
    return false;
  }
  int depth = 0;
  for (const char* cursor = brace; cursor < limit && *cursor; ++cursor) {
    if (*cursor == '{') {
      depth++;
    } else if (*cursor == '}') {
      depth--;
      if (depth == 0) {
        *out_start = brace + 1;
        *out_end = cursor;
        return true;
      }
    }
  }
  return false;
}

static bool FindArrayBoundsWithin(const char* start,
                                  const char* limit,
                                  const char* key,
                                  const char** out_start,
                                  const char** out_end) {
  if (!start || !limit || !key || !out_start || !out_end) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(start, pattern);
  if (!key_pos || key_pos >= limit) {
    return false;
  }
  const char* bracket = std::strchr(key_pos + std::strlen(pattern), '[');
  if (!bracket || bracket >= limit) {
    return false;
  }
  int depth = 0;
  for (const char* cursor = bracket; cursor < limit && *cursor; ++cursor) {
    if (*cursor == '[') {
      depth++;
    } else if (*cursor == ']') {
      depth--;
      if (depth == 0) {
        *out_start = bracket + 1;
        *out_end = cursor;
        return true;
      }
    }
  }
  return false;
}

static bool IsValidHhmm(uint32_t hhmm) {
  if (hhmm > 2359) {
    return false;
  }
  const uint32_t mm = hhmm % 100;
  return mm < 60;
}

static bool IsValidDaysMask(uint32_t mask) {
  return mask <= 0x7F;
}

static bool ExtractStringFieldWithin(const char* start,
                                     const char* limit,
                                     const char* key,
                                     char* out,
                                     size_t out_size) {
  if (!start || !limit || !key || !out || out_size == 0) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(start, pattern);
  if (!key_pos || key_pos >= limit) {
    return false;
  }
  const char* colon = std::strchr(key_pos + std::strlen(pattern), ':');
  if (!colon || colon >= limit) {
    return false;
  }
  const char* value = SkipWs(colon + 1);
  if (!value || value >= limit || *value != '"') {
    return false;
  }
  ++value;
  size_t written = 0;
  while (value < limit && *value && *value != '"' && written + 1 < out_size) {
    out[written++] = *value++;
  }
  if (value >= limit || *value != '"') {
    return false;
  }
  out[written] = '\0';
  return true;
}

static bool IsSafeWifiField(const char* value) {
  if (!value) {
    return false;
  }
  size_t len = 0;
  while (value[len] != '\0') {
    char ch = value[len];
    if (ch == '"' || ch == '\\' || static_cast<unsigned char>(ch) < 0x20) {
      return false;
    }
    ++len;
    if (len > 64) {
      return false;
    }
  }
  return len > 0;
}

ScenariosConfig DefaultScenariosConfig() {
  ScenariosConfig config{};
  config.schema_version = kScenariosSchemaVersion;
  config.water_moisture.enabled = false;
  config.water_moisture.min_time_between_watering_s = 0;
  for (size_t i = 0; i < kMaxSoilSensors; ++i) {
    config.water_moisture.sensors[i].port = static_cast<uint8_t>(i);
    config.water_moisture.sensors[i].enabled = false;
    config.water_moisture.sensors[i].threshold_percent = 30;
    config.water_moisture.sensors[i].duration_s = 0;
  }

  config.water_schedule.enabled = false;
  config.water_schedule.entry_count = 0;
  for (size_t i = 0; i < kMaxWaterScheduleEntries; ++i) {
    config.water_schedule.entries[i].start_hhmm = 0;
    config.water_schedule.entries[i].duration_s = 0;
    config.water_schedule.entries[i].days_mask = 0;
  }

  config.light_schedule.enabled = false;
  config.light_schedule.entry_count = 0;
  for (size_t i = 0; i < kMaxLightScheduleEntries; ++i) {
    config.light_schedule.entries[i].start_hhmm = 0;
    config.light_schedule.entries[i].end_hhmm = 0;
    config.light_schedule.entries[i].days_mask = 0;
  }
  return config;
}

bool EncodeScenariosConfig(const ScenariosConfig& config, char* out, size_t out_size) {
  if (!out || out_size == 0) {
    return false;
  }
  size_t offset = 0;
  auto append = [&](const char* fmt, ...) -> bool {
    if (offset >= out_size) {
      return false;
    }
    va_list args;
    va_start(args, fmt);
    const int written = std::vsnprintf(out + offset, out_size - offset, fmt, args);
    va_end(args);
    if (written <= 0 || static_cast<size_t>(written) >= out_size - offset) {
      return false;
    }
    offset += static_cast<size_t>(written);
    return true;
  };

  if (!append("{\"schema_version\":%u", static_cast<unsigned int>(config.schema_version))) {
    return false;
  }
  if (!append(",\"watering\":{")) {
    return false;
  }
  if (!append("\"by_moisture\":{")) {
    return false;
  }
  if (!append("\"enabled\":%s,", config.water_moisture.enabled ? "true" : "false")) {
    return false;
  }
  if (!append("\"min_time_between_watering_s\":%u,",
              static_cast<unsigned int>(config.water_moisture.min_time_between_watering_s))) {
    return false;
  }
  if (!append("\"per_sensor\":[")) {
    return false;
  }
  for (size_t i = 0; i < kMaxSoilSensors; ++i) {
    const MoistureSensorConfig& sensor = config.water_moisture.sensors[i];
    if (!append("{\"port\":%u,\"enabled\":%s,\"threshold_percent\":%u,\"duration_s\":%u}",
                static_cast<unsigned int>(sensor.port),
                sensor.enabled ? "true" : "false",
                static_cast<unsigned int>(sensor.threshold_percent),
                static_cast<unsigned int>(sensor.duration_s))) {
      return false;
    }
    if (i + 1 < kMaxSoilSensors) {
      if (!append(",")) {
        return false;
      }
    }
  }
  if (!append("]},")) {
    return false;
  }

  if (!append("\"by_schedule\":{")) {
    return false;
  }
  if (!append("\"enabled\":%s,", config.water_schedule.enabled ? "true" : "false")) {
    return false;
  }
  if (!append("\"entries\":[")) {
    return false;
  }
  for (size_t i = 0; i < config.water_schedule.entry_count; ++i) {
    const WaterScheduleEntry& entry = config.water_schedule.entries[i];
    if (!append("{\"start_hhmm\":%u,\"duration_s\":%u,\"days_mask\":%u}",
                static_cast<unsigned int>(entry.start_hhmm),
                static_cast<unsigned int>(entry.duration_s),
                static_cast<unsigned int>(entry.days_mask))) {
      return false;
    }
    if (i + 1 < config.water_schedule.entry_count) {
      if (!append(",")) {
        return false;
      }
    }
  }
  if (!append("]}},")) {
    return false;
  }

  if (!append("\"light\":{\"by_schedule\":{")) {
    return false;
  }
  if (!append("\"enabled\":%s,", config.light_schedule.enabled ? "true" : "false")) {
    return false;
  }
  if (!append("\"entries\":[")) {
    return false;
  }
  for (size_t i = 0; i < config.light_schedule.entry_count; ++i) {
    const LightScheduleEntry& entry = config.light_schedule.entries[i];
    if (!append("{\"start_hhmm\":%u,\"end_hhmm\":%u,\"days_mask\":%u}",
                static_cast<unsigned int>(entry.start_hhmm),
                static_cast<unsigned int>(entry.end_hhmm),
                static_cast<unsigned int>(entry.days_mask))) {
      return false;
    }
    if (i + 1 < config.light_schedule.entry_count) {
      if (!append(",")) {
        return false;
      }
    }
  }
  if (!append("]}}}")) {
    return false;
  }

  if (!append("}")) {
    return false;
  }
  return true;
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
  if (schema_version == 1) {
    bool value = false;
    if (ExtractBoolAfterKey(json, "water_time", "enabled", value)) {
      parsed.water_schedule.enabled = value;
    }
    if (ExtractBoolAfterKey(json, "water_moisture", "enabled", value)) {
      parsed.water_moisture.enabled = value;
    }
    if (ExtractBoolAfterKey(json, "light_schedule", "enabled", value)) {
      parsed.light_schedule.enabled = value;
    }
    parsed.schema_version = kScenariosSchemaVersion;
    *config = parsed;
    return true;
  }

  if (schema_version != kScenariosSchemaVersion) {
    return false;
  }

  const char* watering_start = nullptr;
  const char* watering_end = nullptr;
  if (FindObjectBoundsWithin(json, json + std::strlen(json), "watering", &watering_start, &watering_end)) {
    const char* by_moisture_start = nullptr;
    const char* by_moisture_end = nullptr;
    if (FindObjectBoundsWithin(watering_start, watering_end, "by_moisture", &by_moisture_start, &by_moisture_end)) {
      bool enabled = parsed.water_moisture.enabled;
      if (ExtractBoolWithin(by_moisture_start, by_moisture_end, "enabled", enabled)) {
        parsed.water_moisture.enabled = enabled;
      }
      uint32_t min_time = parsed.water_moisture.min_time_between_watering_s;
      if (ExtractUintFieldWithin(by_moisture_start, by_moisture_end, "min_time_between_watering_s", min_time)) {
        parsed.water_moisture.min_time_between_watering_s = min_time;
      }

      const char* sensors_start = nullptr;
      const char* sensors_end = nullptr;
      if (FindArrayBoundsWithin(by_moisture_start, by_moisture_end, "per_sensor", &sensors_start, &sensors_end)) {
        const char* cursor = sensors_start;
        while (cursor < sensors_end) {
          const char* obj_start = std::strchr(cursor, '{');
          if (!obj_start || obj_start >= sensors_end) {
            break;
          }
          const char* obj_end = std::strchr(obj_start, '}');
          if (!obj_end || obj_end >= sensors_end) {
            break;
          }
          uint32_t port = 0;
          if (ExtractUintFieldWithin(obj_start, obj_end, "port", port) && port < kMaxSoilSensors) {
            MoistureSensorConfig& sensor = parsed.water_moisture.sensors[port];
            sensor.port = static_cast<uint8_t>(port);
            bool sen_enabled = sensor.enabled;
            if (ExtractBoolWithin(obj_start, obj_end, "enabled", sen_enabled)) {
              sensor.enabled = sen_enabled;
            }
            uint32_t threshold = sensor.threshold_percent;
            if (ExtractUintFieldWithin(obj_start, obj_end, "threshold_percent", threshold)) {
              sensor.threshold_percent = static_cast<uint8_t>(threshold);
            }
            uint32_t duration = sensor.duration_s;
            if (ExtractUintFieldWithin(obj_start, obj_end, "duration_s", duration)) {
              sensor.duration_s = duration;
            }
          }
          cursor = obj_end + 1;
        }
      }
    }

    const char* by_schedule_start = nullptr;
    const char* by_schedule_end = nullptr;
    if (FindObjectBoundsWithin(watering_start, watering_end, "by_schedule", &by_schedule_start, &by_schedule_end)) {
      bool enabled = parsed.water_schedule.enabled;
      if (ExtractBoolWithin(by_schedule_start, by_schedule_end, "enabled", enabled)) {
        parsed.water_schedule.enabled = enabled;
      }
      const char* entries_start = nullptr;
      const char* entries_end = nullptr;
      if (FindArrayBoundsWithin(by_schedule_start, by_schedule_end, "entries", &entries_start, &entries_end)) {
        const char* cursor = entries_start;
        size_t count = 0;
        while (cursor < entries_end && count < kMaxWaterScheduleEntries) {
          const char* obj_start = std::strchr(cursor, '{');
          if (!obj_start || obj_start >= entries_end) {
            break;
          }
          const char* obj_end = std::strchr(obj_start, '}');
          if (!obj_end || obj_end >= entries_end) {
            break;
          }
          WaterScheduleEntry entry{};
          uint32_t start_hhmm = 0;
          uint32_t duration = 0;
          uint32_t days_mask = 0;
          if (ExtractUintFieldWithin(obj_start, obj_end, "start_hhmm", start_hhmm) &&
              ExtractUintFieldWithin(obj_start, obj_end, "duration_s", duration) &&
              ExtractUintFieldWithin(obj_start, obj_end, "days_mask", days_mask)) {
            entry.start_hhmm = static_cast<uint16_t>(start_hhmm);
            entry.duration_s = static_cast<uint16_t>(duration);
            entry.days_mask = static_cast<uint8_t>(days_mask);
            parsed.water_schedule.entries[count++] = entry;
          }
          cursor = obj_end + 1;
        }
        parsed.water_schedule.entry_count = count;
      }
    }
  }

  const char* light_start = nullptr;
  const char* light_end = nullptr;
  if (FindObjectBoundsWithin(json, json + std::strlen(json), "light", &light_start, &light_end)) {
    const char* by_schedule_start = nullptr;
    const char* by_schedule_end = nullptr;
    if (FindObjectBoundsWithin(light_start, light_end, "by_schedule", &by_schedule_start, &by_schedule_end)) {
      bool enabled = parsed.light_schedule.enabled;
      if (ExtractBoolWithin(by_schedule_start, by_schedule_end, "enabled", enabled)) {
        parsed.light_schedule.enabled = enabled;
      }
      const char* entries_start = nullptr;
      const char* entries_end = nullptr;
      if (FindArrayBoundsWithin(by_schedule_start, by_schedule_end, "entries", &entries_start, &entries_end)) {
        const char* cursor = entries_start;
        size_t count = 0;
        while (cursor < entries_end && count < kMaxLightScheduleEntries) {
          const char* obj_start = std::strchr(cursor, '{');
          if (!obj_start || obj_start >= entries_end) {
            break;
          }
          const char* obj_end = std::strchr(obj_start, '}');
          if (!obj_end || obj_end >= entries_end) {
            break;
          }
          LightScheduleEntry entry{};
          uint32_t start_hhmm = 0;
          uint32_t end_hhmm = 0;
          uint32_t days_mask = 0;
          if (ExtractUintFieldWithin(obj_start, obj_end, "start_hhmm", start_hhmm) &&
              ExtractUintFieldWithin(obj_start, obj_end, "end_hhmm", end_hhmm) &&
              ExtractUintFieldWithin(obj_start, obj_end, "days_mask", days_mask)) {
            entry.start_hhmm = static_cast<uint16_t>(start_hhmm);
            entry.end_hhmm = static_cast<uint16_t>(end_hhmm);
            entry.days_mask = static_cast<uint8_t>(days_mask);
            parsed.light_schedule.entries[count++] = entry;
          }
          cursor = obj_end + 1;
        }
        parsed.light_schedule.entry_count = count;
      }
    }
  }

  parsed.schema_version = kScenariosSchemaVersion;
  *config = parsed;
  return true;
}

bool ValidateScenariosConfig(const ScenariosConfig& config) {
  if (config.schema_version != kScenariosSchemaVersion) {
    return false;
  }
  if (config.water_moisture.min_time_between_watering_s > 86400U * 365U) {
    return false;
  }
  for (size_t i = 0; i < kMaxSoilSensors; ++i) {
    const MoistureSensorConfig& sensor = config.water_moisture.sensors[i];
    if (sensor.port != i) {
      return false;
    }
    if (sensor.threshold_percent > 100) {
      return false;
    }
  }
  if (config.water_schedule.entry_count > kMaxWaterScheduleEntries) {
    return false;
  }
  for (size_t i = 0; i < config.water_schedule.entry_count; ++i) {
    const WaterScheduleEntry& entry = config.water_schedule.entries[i];
    if (!IsValidHhmm(entry.start_hhmm)) {
      return false;
    }
    if (entry.duration_s == 0) {
      return false;
    }
    if (!IsValidDaysMask(entry.days_mask)) {
      return false;
    }
  }
  if (config.light_schedule.entry_count > kMaxLightScheduleEntries) {
    return false;
  }
  for (size_t i = 0; i < config.light_schedule.entry_count; ++i) {
    const LightScheduleEntry& entry = config.light_schedule.entries[i];
    if (!IsValidHhmm(entry.start_hhmm) || !IsValidHhmm(entry.end_hhmm)) {
      return false;
    }
    if (!IsValidDaysMask(entry.days_mask)) {
      return false;
    }
  }
  return true;
}

bool EncodeWifiConfig(const char* const* ssids,
                      const char* const* passwords,
                      size_t count,
                      char* out,
                      size_t out_size) {
  if (!out || out_size == 0) {
    return false;
  }
  if (!ssids || !passwords || count == 0) {
    return false;
  }
  for (size_t i = 0; i < count; ++i) {
    if (!IsSafeWifiField(ssids[i])) {
      return false;
    }
    const char* pass = passwords[i] ? passwords[i] : "";
    if (std::strlen(pass) > 64) {
      return false;
    }
    for (const char* p = pass; *p; ++p) {
      if (*p == '"' || *p == '\\' || static_cast<unsigned char>(*p) < 0x20) {
        return false;
      }
    }
  }
  size_t offset = 0;
  auto append = [&](const char* fmt, ...) -> bool {
    if (offset >= out_size) {
      return false;
    }
    va_list args;
    va_start(args, fmt);
    const int written = std::vsnprintf(out + offset, out_size - offset, fmt, args);
    va_end(args);
    if (written <= 0 || static_cast<size_t>(written) >= out_size - offset) {
      return false;
    }
    offset += static_cast<size_t>(written);
    return true;
  };
  if (!append("{\"schema_version\":%u,\"networks\":[", static_cast<unsigned int>(kWifiSchemaVersion))) {
    return false;
  }
  for (size_t i = 0; i < count; ++i) {
    const char* pass = passwords[i] ? passwords[i] : "";
    if (!append("{\"ssid\":\"%s\",\"password\":\"%s\"}", ssids[i], pass)) {
      return false;
    }
    if (i + 1 < count) {
      if (!append(",")) {
        return false;
      }
    }
  }
  if (!append("]}")) {
    return false;
  }
  return true;
}

bool EncodeWifiConfig(const char* ssid, const char* password, char* out, size_t out_size) {
  const char* ssids[1] = {ssid};
  const char* passwords[1] = {password};
  return EncodeWifiConfig(ssids, passwords, 1, out, out_size);
}

bool ValidateWifiConfig(const char* json) {
  if (!json || !HasJsonBraces(json)) {
    return false;
  }
  uint32_t schema_version = 0;
  if (!ExtractUintField(json, "schema_version", schema_version)) {
    return false;
  }
  if (schema_version != kWifiSchemaVersion) {
    return false;
  }
  const char* array_start = nullptr;
  const char* array_end = nullptr;
  if (!FindArrayBoundsWithin(json, json + std::strlen(json), "networks", &array_start, &array_end)) {
    return false;
  }
  bool has_ssid = false;
  const char* cursor = array_start;
  while (cursor < array_end) {
    const char* obj_start = std::strchr(cursor, '{');
    if (!obj_start || obj_start >= array_end) {
      break;
    }
    const char* obj_end = std::strchr(obj_start, '}');
    if (!obj_end || obj_end > array_end) {
      return false;
    }
    char ssid[65];
    if (ExtractStringFieldWithin(obj_start, obj_end, "ssid", ssid, sizeof(ssid))) {
      if (ssid[0] != '\0') {
        has_ssid = true;
      }
    }
    cursor = obj_end + 1;
  }
  return has_ssid;
}

}
