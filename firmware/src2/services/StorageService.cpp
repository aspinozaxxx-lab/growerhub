#include "services/StorageService.h"

#include <cstdio>
#include <cstring>

#include "util/JsonUtil.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <LittleFS.h>
#endif

#if defined(UNIT_TEST)
#include <cerrno>
#include <fstream>

#if defined(_WIN32)
#include <direct.h>
#else
#include <sys/stat.h>
#endif
#endif

namespace Services {

#if defined(UNIT_TEST)
static bool MakeDir(const char* path) {
#if defined(_WIN32)
  if (_mkdir(path) == 0) {
    return true;
  }
#else
  if (mkdir(path, 0755) == 0) {
    return true;
  }
#endif
  return errno == EEXIST;
}
#endif

#if defined(ARDUINO)
static const char* kCfgDir = "/cfg"; // katalog dlya cfg
static const char* kScenariosPath = "/cfg/scenarios.json"; // defolt scenarii v2
static const char* kDevicePath = "/cfg/device.json"; // sostoyanie OTA

static bool EnsureCfgDir() {
  // sozdanie /cfg dlya chistogo LittleFS
  if (LittleFS.exists(kCfgDir)) {
    return true;
  }
  return LittleFS.mkdir(kCfgDir);
}

static bool EnsureDefaultScenarios(StorageService& storage) {
  // sozdanie defoltnogo scenarios.json pri otsutstvii
  if (storage.Exists(kScenariosPath)) {
    return true;
  }
  Util::ScenariosConfig config = Util::DefaultScenariosConfig();
  char payload[768];
  if (!Util::EncodeScenariosConfig(config, payload, sizeof(payload))) {
    return false;
  }
  return storage.WriteFileAtomic(kScenariosPath, payload);
}

static bool EnsureDefaultDevice(StorageService& storage) {
  // sozdanie defoltnogo device.json pri otsutstvii
  if (storage.Exists(kDevicePath)) {
    return true;
  }
  char payload[160];
  const int written = std::snprintf(
      payload,
      sizeof(payload),
      "{\"schema_version\":1,\"pending\":false,\"boot_fail_count\":0,\"pending_since_ms\":0}");
  if (written <= 0 || static_cast<size_t>(written) >= sizeof(payload)) {
    return false;
  }
  return storage.WriteFileAtomic(kDevicePath, payload);
}
#endif

void StorageService::Init(Core::Context& ctx) {
  (void)ctx;
  Util::Logger::Info("init StorageService");

#if defined(ARDUINO)
  bool mounted = LittleFS.begin();
  if (!mounted) {
    Util::Logger::Info("littlefs mount fail, format");
    mounted = LittleFS.begin(true);
  }
  if (!mounted) {
    Util::Logger::Info("littlefs mount fail after format");
  } else {
    if (!EnsureCfgDir()) {
      Util::Logger::Info("littlefs mkdir /cfg fail");
    }
    if (!EnsureDefaultScenarios(*this)) {
      Util::Logger::Info("littlefs default scenarios fail");
    }
    if (!EnsureDefaultDevice(*this)) {
      Util::Logger::Info("littlefs default device fail");
    }
  }
#endif

#if defined(UNIT_TEST)
  if (root_path_[0] == '\0') {
    std::snprintf(root_path_, sizeof(root_path_), "test2/tmp/test_storage");
  }
  MakeDir(root_path_);
#endif
}

bool StorageService::ReadFile(const char* path, char* out, size_t out_size) {
  if (!path || !out || out_size == 0) {
    return false;
  }

  char full_path[256];
  if (!BuildPath(path, full_path, sizeof(full_path))) {
    return false;
  }

#if defined(ARDUINO)
  File file = LittleFS.open(full_path, "r");
  if (!file) {
    return false;
  }
  size_t read_len = file.readBytes(out, out_size - 1);
  out[read_len] = '\0';
  file.close();
  return true;
#else
  std::ifstream file(full_path, std::ios::in | std::ios::binary);
  if (!file.is_open()) {
    return false;
  }
  file.read(out, static_cast<std::streamsize>(out_size - 1));
  std::streamsize read_len = file.gcount();
  out[read_len] = '\0';
  return true;
#endif
}

bool StorageService::WriteFileAtomic(const char* path, const char* payload) {
  if (!path || !payload) {
    return false;
  }

  char full_path[256];
  if (!BuildPath(path, full_path, sizeof(full_path))) {
    return false;
  }

  if (!EnsureDirForPath(full_path)) {
    return false;
  }

  char temp_path[272];
  std::snprintf(temp_path, sizeof(temp_path), "%s.tmp", full_path);

#if defined(ARDUINO)
  File file = LittleFS.open(temp_path, "w");
  if (!file) {
    return false;
  }
  size_t written = file.print(payload);
  file.close();
  if (written == 0) {
    LittleFS.remove(temp_path);
    return false;
  }
  if (LittleFS.exists(full_path)) {
    LittleFS.remove(full_path);
  }
  if (!LittleFS.rename(temp_path, full_path)) {
    LittleFS.remove(temp_path);
    return false;
  }
  return true;
#else
  {
    std::ofstream file(temp_path, std::ios::out | std::ios::binary | std::ios::trunc);
    if (!file.is_open()) {
      return false;
    }
    file.write(payload, static_cast<std::streamsize>(std::strlen(payload)));
  }
  std::remove(full_path);
  if (std::rename(temp_path, full_path) != 0) {
    std::remove(temp_path);
    return false;
  }
  return true;
#endif
}

bool StorageService::Exists(const char* path) const {
  if (!path) {
    return false;
  }

  char full_path[256];
  if (!BuildPath(path, full_path, sizeof(full_path))) {
    return false;
  }

#if defined(ARDUINO)
  return LittleFS.exists(full_path);
#else
  FILE* file = std::fopen(full_path, "r");
  if (file) {
    std::fclose(file);
    return true;
  }
  return false;
#endif
}

#if defined(UNIT_TEST)
void StorageService::SetRootForTests(const char* root_path) {
  if (!root_path) {
    root_path_[0] = '\0';
    return;
  }
  std::snprintf(root_path_, sizeof(root_path_), "%s", root_path);
}
#endif

bool StorageService::BuildPath(const char* path, char* out, size_t out_size) const {
  if (!path || !out || out_size == 0) {
    return false;
  }

#if defined(UNIT_TEST)
  const char* root = root_path_[0] != '\0' ? root_path_ : "test2/tmp/test_storage";
  const char* rel = path[0] == '/' ? path + 1 : path;
  const int written = std::snprintf(out, out_size, "%s/%s", root, rel);
  return written > 0 && static_cast<size_t>(written) < out_size;
#else
  const int written = std::snprintf(out, out_size, "%s", path);
  return written > 0 && static_cast<size_t>(written) < out_size;
#endif
}

bool StorageService::EnsureDirForPath(const char* path) const {
  if (!path) {
    return false;
  }

  const char* last_sep = std::strrchr(path, '/');
  if (!last_sep) {
    return true;
  }

  char dir[256];
  size_t len = static_cast<size_t>(last_sep - path);
  if (len >= sizeof(dir)) {
    return false;
  }
  std::memcpy(dir, path, len);
  dir[len] = '\0';

#if defined(ARDUINO)
  if (LittleFS.exists(dir)) {
    return true;
  }
  return LittleFS.mkdir(dir);
#else
  if (dir[0] == '\0') {
    return true;
  }

  char current[256];
  size_t current_len = 0;
  for (size_t i = 0; i < len; ++i) {
    current[current_len++] = dir[i];
    if (dir[i] == '/') {
      current[current_len - 1] = '\0';
      if (current[0] != '\0') {
        if (!MakeDir(current)) {
          return false;
        }
      }
      current[current_len - 1] = '/';
    }
  }
  current[current_len] = '\0';
  return MakeDir(current);
#endif
}

}
