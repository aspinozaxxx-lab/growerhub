/*
 * Chto v faile: realizaciya JSON kodirovaniya Wi-Fi konfiguracii.
 * Rol v arhitekture: util.
 * Naznachenie: validaciya i bezopasnaya serializaciya spiska Wi-Fi setei.
 * Soderzhit: kodirovanie i proverku shemy wifi.json.
 */

#include "util/JsonUtil.h"

#include <cctype>
#include <cstdarg>
#include <cstdio>
#include <cstring>

namespace Util {

namespace {
const char* SkipWs(const char* ptr) {
  const char* current = ptr;
  while (current && *current && std::isspace(static_cast<unsigned char>(*current))) {
    ++current;
  }
  return current;
}

bool HasJsonBraces(const char* json) {
  if (!json) {
    return false;
  }
  const char* left = std::strchr(json, '{');
  const char* right = std::strrchr(json, '}');
  return left && right && left < right;
}

bool ExtractUintField(const char* json, const char* key, uint32_t& out) {
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
  const char* value = colon ? SkipWs(colon + 1) : nullptr;
  if (!value || !std::isdigit(static_cast<unsigned char>(*value))) {
    return false;
  }
  uint32_t result = 0;
  while (*value && std::isdigit(static_cast<unsigned char>(*value))) {
    result = result * 10U + static_cast<uint32_t>(*value - '0');
    ++value;
  }
  out = result;
  return true;
}

bool FindArrayBounds(const char* json,
                     const char* key,
                     const char** out_start,
                     const char** out_end) {
  if (!json || !key || !out_start || !out_end) {
    return false;
  }
  char pattern[64];
  std::snprintf(pattern, sizeof(pattern), "\"%s\"", key);
  const char* key_pos = std::strstr(json, pattern);
  const char* bracket = key_pos ? std::strchr(key_pos + std::strlen(pattern), '[') : nullptr;
  if (!bracket) {
    return false;
  }
  int depth = 0;
  for (const char* cursor = bracket; *cursor; ++cursor) {
    if (*cursor == '[') {
      ++depth;
    } else if (*cursor == ']' && --depth == 0) {
      *out_start = bracket + 1;
      *out_end = cursor;
      return true;
    }
  }
  return false;
}

bool ExtractStringWithin(const char* start,
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
  const char* value = colon ? SkipWs(colon + 1) : nullptr;
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

bool IsSafeField(const char* value, bool allow_empty) {
  if (!value) {
    return false;
  }
  size_t len = 0;
  while (value[len] != '\0') {
    const unsigned char ch = static_cast<unsigned char>(value[len]);
    if (ch == '"' || ch == '\\' || ch < 0x20) {
      return false;
    }
    if (++len > 64) {
      return false;
    }
  }
  return allow_empty || len > 0;
}
} // namespace

bool IsSupportedWifiSchemaVersion(uint32_t schema_version) {
  return schema_version == kWifiLegacySchemaVersion || schema_version == kWifiSchemaVersion;
}

bool EncodeWifiConfig(const char* const* ssids,
                      const char* const* passwords,
                      size_t count,
                      char* out,
                      size_t out_size) {
  if (!ssids || !passwords || !out || out_size == 0 || count == 0) {
    return false;
  }
  for (size_t i = 0; i < count; ++i) {
    if (!IsSafeField(ssids[i], false) || !IsSafeField(passwords[i] ? passwords[i] : "", true)) {
      return false;
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

  if (!append("{\"schema_version\":%u,\"networks\":[",
              static_cast<unsigned int>(kWifiSchemaVersion))) {
    return false;
  }
  for (size_t i = 0; i < count; ++i) {
    if (!append("{\"ssid\":\"%s\",\"password\":\"%s\"}",
                ssids[i],
                passwords[i] ? passwords[i] : "")) {
      return false;
    }
    if (i + 1 < count && !append(",")) {
      return false;
    }
  }
  return append("]}");
}

bool EncodeWifiConfig(const char* ssid, const char* password, char* out, size_t out_size) {
  const char* ssids[1] = {ssid};
  const char* passwords[1] = {password};
  return EncodeWifiConfig(ssids, passwords, 1, out, out_size);
}

bool ValidateWifiConfig(const char* json) {
  if (!HasJsonBraces(json)) {
    return false;
  }
  uint32_t schema_version = 0;
  if (!ExtractUintField(json, "schema_version", schema_version) ||
      !IsSupportedWifiSchemaVersion(schema_version)) {
    return false;
  }
  const char* array_start = nullptr;
  const char* array_end = nullptr;
  if (!FindArrayBounds(json, "networks", &array_start, &array_end)) {
    return false;
  }

  const char* cursor = array_start;
  while (cursor < array_end) {
    const char* object_start = std::strchr(cursor, '{');
    if (!object_start || object_start >= array_end) {
      break;
    }
    const char* object_end = std::strchr(object_start, '}');
    if (!object_end || object_end > array_end) {
      return false;
    }
    char ssid[65];
    if (ExtractStringWithin(object_start, object_end, "ssid", ssid, sizeof(ssid)) &&
        ssid[0] != '\0') {
      return true;
    }
    cursor = object_end + 1;
  }
  return false;
}

} // namespace Util
