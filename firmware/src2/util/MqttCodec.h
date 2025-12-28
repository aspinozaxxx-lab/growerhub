#pragma once

#include <cstddef>
#include <cstdint>

namespace Util {

static const size_t kCorrelationIdMax = 64;

enum class CommandType : uint8_t {
  kUnknown = 0,
  kPumpStart = 1,
  kPumpStop = 2,
  kReboot = 3
};

enum class ParseError : uint8_t {
  kNone = 0,
  kInvalidJson = 1,
  kTypeMissing = 2,
  kDurationMissingOrInvalid = 3,
  kUnsupportedCommand = 4
};

struct Command {
  CommandType type;
  uint32_t duration_s;
  char correlation_id[kCorrelationIdMax];
};

bool ParseCommand(const char* json, Command& out, ParseError& error);
const char* ParseErrorReason(ParseError error);

bool BuildAckStatus(const char* correlation_id, const char* result, const char* status,
                    char* out, size_t out_size);
bool BuildAckError(const char* correlation_id, const char* reason, char* out, size_t out_size);

}
