#pragma once

#ifndef GH_FW_VERSION
#define GH_FW_VERSION "v2-dev"
#endif

#ifndef GH_FW_VER
#define GH_FW_VER "v2-dev"
#endif

#ifndef GH_FW_NAME
#define GH_FW_NAME "growerhub-v2"
#endif

#ifndef GH_FW_BUILD
#define GH_FW_BUILD "dev"
#endif

namespace Config {

static constexpr const char* kFwVersion = GH_FW_VERSION;
static constexpr const char* kFwVer = GH_FW_VER;
static constexpr const char* kFwName = GH_FW_NAME;
static constexpr const char* kFwBuild = GH_FW_BUILD;
static constexpr bool kFwInfoAvailable = true;

}
