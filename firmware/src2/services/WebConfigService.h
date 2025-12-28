#pragma once

#include <cstddef>

#include "core/Context.h"

#if defined(ARDUINO)
class WebServer;
#endif

namespace Services {

class WebConfigService {
 public:
  void Init(Core::Context& ctx);
  void Loop(Core::Context& ctx);
  static bool BuildWifiConfigJson(const char* ssid, const char* password, char* out, size_t out_size);

 private:
 StorageService* storage_ = nullptr;
 Core::EventQueue* event_queue_ = nullptr;
 const char* device_id_ = nullptr;
#if defined(ARDUINO)
  WebServer* server_ = nullptr;
  bool server_started_ = false;
#endif
};

}
