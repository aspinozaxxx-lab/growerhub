#include "util/MqttCodec.h"

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

static bool ExtractStringField(const char* json, const char* key, char* out, size_t out_size) {
  if (!json || !key || !out || out_size == 0) {
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
  if (!value || *value != '"') {
    return false;
  }
  ++value;
  const char* end = std::strchr(value, '"');
  if (!end) {
    return false;
  }
  size_t len = static_cast<size_t>(end - value);
  if (len >= out_size) {
    len = out_size - 1;
  }
  std::memcpy(out, value, len);
  out[len] = '\0';
  return true;
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

bool ParseCommand(const char* json, Command& out, ParseError& error) {
  out.type = CommandType::kUnknown;
  out.duration_s = 0;
  out.correlation_id[0] = '\0';
  error = ParseError::kNone;

  if (!HasJsonBraces(json)) {
    error = ParseError::kInvalidJson;
    return false;
  }

  ExtractStringField(json, "correlation_id", out.correlation_id, sizeof(out.correlation_id));

  char type_buf[32];
  if (!ExtractStringField(json, "type", type_buf, sizeof(type_buf))) {
    error = ParseError::kTypeMissing;
    return false;
  }

  if (std::strcmp(type_buf, "pump.start") == 0) {
    out.type = CommandType::kPumpStart;
    uint32_t duration_s = 0;
    if (!ExtractUintField(json, "duration_s", duration_s) || duration_s == 0) {
      error = ParseError::kDurationMissingOrInvalid;
      return false;
    }
    out.duration_s = duration_s;
    return true;
  }

  if (std::strcmp(type_buf, "pump.stop") == 0) {
    out.type = CommandType::kPumpStop;
    return true;
  }

  if (std::strcmp(type_buf, "reboot") == 0) {
    out.type = CommandType::kReboot;
    return true;
  }

  error = ParseError::kUnsupportedCommand;
  return false;
}

const char* ParseErrorReason(ParseError error) {
  switch (error) {
    case ParseError::kInvalidJson:
      return "bad command format: invalid JSON";
    case ParseError::kTypeMissing:
      return "bad command format: type missing";
    case ParseError::kDurationMissingOrInvalid:
      return "bad command format: duration_s missing or invalid";
    case ParseError::kUnsupportedCommand:
      return "unsupported command type";
    case ParseError::kNone:
    default:
      return "unknown";
  }
}

bool BuildAckStatus(const char* correlation_id, const char* result, const char* status,
                    char* out, size_t out_size) {
  if (!out || out_size == 0) {
    return false;
  }
  const char* corr = correlation_id ? correlation_id : "";
  const char* res = result ? result : "";
  const char* stat = status ? status : "";
  const int written = std::snprintf(out, out_size,
                                   "{\"correlation_id\":\"%s\",\"result\":\"%s\",\"status\":\"%s\"}",
                                   corr, res, stat);
  return written > 0 && static_cast<size_t>(written) < out_size;
}

bool BuildAckError(const char* correlation_id, const char* reason, char* out, size_t out_size) {
  if (!out || out_size == 0) {
    return false;
  }
  const char* corr = correlation_id ? correlation_id : "";
  const char* why = reason ? reason : "unknown error";
  const int written = std::snprintf(out, out_size,
                                   "{\"correlation_id\":\"%s\",\"result\":\"error\",\"reason\":\"%s\"}",
                                   corr, why);
  return written > 0 && static_cast<size_t>(written) < out_size;
}

}
