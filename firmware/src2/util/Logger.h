/*
 * Chto v faile: obyavleniya prostogo logirovaniya.
 * Rol v arhitekture: util.
 * Naznachenie: publichnyi API i tipy dlya sloya util.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

namespace Services {
class TimeService;
}

namespace Util {

class Logger {
 public:
  /**
   * Initsializiruet kanal logirovaniya.
   */
  static void Init();
  /**
   * Ustanavlivaet provider vremeni dlya log-prefiksa.
   * @param time_service Ukazatel na servis vremeni.
   */
  static void SetTimeProvider(const Services::TimeService* time_service);
  /**
   * Pechataet infosoobshchenie v log.
   * @param message Stroka soobshcheniya dlya loga.
   */
  static void Info(const char* message);

#if defined(UNIT_TEST)
  /**
   * Vozvrashaet poslednyuyu zapisannuyu stroku dlya testov.
   */
  static const char* GetLastMessageForTests();
  /**
   * Ochishchaet testovyi bufer loga.
   */
  static void ClearLastMessageForTests();
#endif
};

}


