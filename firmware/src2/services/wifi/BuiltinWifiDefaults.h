#pragma once

#include <cstddef>

namespace Services {

struct BuiltinWifiNetwork {
  const char* ssid;
  const char* password;
};

static const BuiltinWifiNetwork kBuiltinWifiDefaults[] = {
    {"JR", "qazwsxedc"},
    {"AKADO-E84E", "90838985"},
    {"TP-LINK_446C", "70863765"},
    {"Gfdsa", "qazwsxedc"},
    {"JR2", "qazwsxedc"}};

static const size_t kBuiltinWifiDefaultsCount = 5;

}
