/*
 * Chto v faile: obyavleniya dostupa k hranilishchu konfiguracii.
 * Rol v arhitekture: services.
 * Naznachenie: publichnyi API i tipy dlya sloya services.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>

#include "core/Context.h"

namespace Services {

class StorageService {
 public:
  /**
   * Init servisa hranilishcha.
   * @param ctx Kontekst (dlya sootvetstviya interfeisu).
   */
  void Init(Core::Context& ctx);
  /**
   * Chitaet file po puti v bufer.
   * @param path Put k faylu v FS.
   * @param out Vyhodnoy bufer dlya dannyh.
   * @param out_size Razmer bufera v baytah.
   */
  bool ReadFile(const char* path, char* out, size_t out_size);
  /**
   * Zapisivaet file atomarno (s temp fajlom).
   * @param path Put k faylu v FS.
   * @param payload Stroka dlya zapisi.
   */
  bool WriteFileAtomic(const char* path, const char* payload);
  /**
   * Proveryaet sushchestvovanie fayla.
   * @param path Put k faylu v FS.
   */
  bool Exists(const char* path) const;

#if defined(UNIT_TEST)
  /**
   * Ustanavlivaet kornevoy katalog dlya testov.
   * @param root_path Put k kornevomu katalogu.
   */
  void SetRootForTests(const char* root_path);
#endif

 private:
  bool BuildPath(const char* path, char* out, size_t out_size) const;
  bool EnsureDirForPath(const char* path) const;

#if defined(UNIT_TEST)
  char root_path_[128];
#endif
  bool mounted_ = false;
};

}
