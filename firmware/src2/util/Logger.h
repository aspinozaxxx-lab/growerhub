/*
 * Chto v faile: obyavleniya prostogo logirovaniya.
 * Rol v arhitekture: util.
 * Naznachenie: publichnyi API i tipy dlya sloya util.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

namespace Util {

class Logger {
 public:
  /**
   * Initsializiruet kanal logirovaniya.
   */
  static void Init();
  /**
   * Pechataet infosoobshchenie v log.
   * @param message Stroka soobshcheniya dlya loga.
   */
  static void Info(const char* message);
};

}
