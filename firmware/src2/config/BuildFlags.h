/*
 * Chto v faile: obyavleniya konstant versii i parametrov sborki proshivki.
 * Rol v arhitekture: config.
 * Naznachenie: publichnyi API i tipy dlya sloya config.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

// Makros korotkoi versii proshivki.
#ifndef GH_FW_VER
#define GH_FW_VER "v2-dev551"
#endif

namespace Config {

// Korotkaya stroka versii.
static constexpr const char* kFwVer = GH_FW_VER;

}
