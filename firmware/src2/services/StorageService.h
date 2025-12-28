#pragma once

#include <cstddef>

#include "core/Context.h"

namespace Services {

class StorageService {
 public:
  void Init(Core::Context& ctx);
  bool ReadFile(const char* path, char* out, size_t out_size);
  bool WriteFileAtomic(const char* path, const char* payload);
  bool Exists(const char* path) const;

#if defined(UNIT_TEST)
  void SetRootForTests(const char* root_path);
#endif

 private:
  bool BuildPath(const char* path, char* out, size_t out_size) const;
  bool EnsureDirForPath(const char* path) const;

#if defined(UNIT_TEST)
  char root_path_[128];
#endif
};

}
