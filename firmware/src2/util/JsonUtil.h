#pragma once

#include <cstddef>
#include <cstdint>

namespace Util {

struct ConfigStub {
  uint32_t version;
};

bool EncodeConfig(const ConfigStub& config, char* out, size_t out_size);
bool DecodeConfig(const char* json, ConfigStub* config);
bool ValidateConfig(const ConfigStub& config);

}
