/*
 * Chto v faile: obyavleniya konstant versii i parametrov sborki proshivki.
 * Rol v arhitekture: config.
 * Naznachenie: publichnyi API i tipy dlya sloya config.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

// Makros stroki versii proshivki.
#ifndef GH_FW_VERSION
#define GH_FW_VERSION "v2-dev1312"
#endif

// Makros korotkoi versii proshivki.
#ifndef GH_FW_VER
#define GH_FW_VER "v2-dev551"
#endif

// Makros imeni proshivki.
#ifndef GH_FW_NAME
#define GH_FW_NAME "growerhub-v2"
#endif

// Makros metki sborki.
#ifndef GH_FW_BUILD
#define GH_FW_BUILD "dev"
#endif

namespace Config {

// Stroka versii proshivki.
static constexpr const char* kFwVersion = GH_FW_VERSION;
// Korotkaya stroka versii.
static constexpr const char* kFwVer = GH_FW_VER;
// Imena proshivki.
static constexpr const char* kFwName = GH_FW_NAME;
// Metka sborki.
static constexpr const char* kFwBuild = GH_FW_BUILD;
// Flag dostupnosti polnoy informacii o sborke.
static constexpr bool kFwInfoAvailable = true;

}
