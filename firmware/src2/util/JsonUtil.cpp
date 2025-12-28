#include "util/JsonUtil.h"

#include <cstring>

namespace Util {

bool EncodeConfig(const ConfigStub& config, char* out, size_t out_size) {
  (void)config;
  if (out == nullptr || out_size < 3) {
    return false;
  }
  out[0] = '{';
  out[1] = '}';
  out[2] = '\0';
  return true;
}

bool DecodeConfig(const char* json, ConfigStub* config) {
  if (json == nullptr || config == nullptr) {
    return false;
  }
  if (std::strcmp(json, "{}") != 0) {
    return false;
  }
  config->version = 1;
  return true;
}

bool ValidateConfig(const ConfigStub& config) {
  return config.version != 0;
}

}
