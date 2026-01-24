/*
 * Chto v faile: obyavleniya vstroennyh setei Wi-Fi po umolchaniyu.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>

namespace Services {

struct BuiltinWifiNetwork {
  // SSID seti.
  const char* ssid;
  // Parol seti.
  const char* password;
};

// Spisok vstroennyh setei po umolchaniyu.
static const BuiltinWifiNetwork kBuiltinWifiDefaults[] = {
    {"JR", "qazwsxedc"},
    {"AKADO-E84E", "90838985"},
    {"TP-LINK_446C", "70863765"},
    {"Gfdsa", "qazwsxedc"},
    {"JR2", "qazwsxedc"}};

// Kolichestvo vstroennyh setei.
static const size_t kBuiltinWifiDefaultsCount = 5;

}
