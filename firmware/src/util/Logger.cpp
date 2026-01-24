/*
 * Chto v faile: realizaciya prostogo logirovaniya.
 * Rol v arhitekture: util.
 * Naznachenie: logika i vzaimodeistvie komponenta v sloe util.
 * Soderzhit: realizacii metodov i vspomogatelnye funkcii.
 */

#include "util/Logger.h"

#include <cstdio>
#include <cstring>

#include "services/TimeService.h"

#if defined(ARDUINO)
#include <Arduino.h>
#endif

namespace Util {

namespace {
  // Ukazatel na servis vremeni dlya prefiksa.
  const Services::TimeService* g_time_service = nullptr;
  // Razmer bufera stroki loga.
  const size_t kLogLineSize = 256;
  // Razmer bufera prefiksa.
  const size_t kPrefixSize = 32;
  // Stroka prefiksa pri otsutstvii vremeni.
  const char* kNoWatchPrefix = "[no watch] ";
#if defined(UNIT_TEST)
  // Testovyi bufer poslednego loga.
  char g_last_message[kLogLineSize] = {0};
#endif

  // Sobiraet prefiks s timestamp ili soobshcheniem ob otsutstvii vremeni.
  void BuildPrefix(char* out, size_t out_size) {
    if (!out || out_size == 0) {
      return;
    }

    char ts_buf[20];
    const bool has_ts = g_time_service && g_time_service->GetLogTimestamp(ts_buf, sizeof(ts_buf));
    if (has_ts) {
      std::snprintf(out, out_size, "[%s] ", ts_buf);
      return;
    }

    std::strncpy(out, kNoWatchPrefix, out_size - 1);
    out[out_size - 1] = '\0';
  }
}

// Inicializaciya logera.
void Logger::Init() {
#if defined(ARDUINO)
  Serial.begin(115200);
#endif
}

// Ustanovka servisa vremeni dlya prefiksa.
void Logger::SetTimeProvider(const Services::TimeService* time_service) {
  g_time_service = time_service;
}

// Pechat soobshcheniya s vremennym prefiksom.
void Logger::Info(const char* message) {
  char prefix[kPrefixSize];
  BuildPrefix(prefix, sizeof(prefix));

  const char* safe_message = message ? message : "";
  char line[kLogLineSize];
  std::snprintf(line, sizeof(line), "%s%s", prefix, safe_message);

#if defined(UNIT_TEST)
  std::strncpy(g_last_message, line, sizeof(g_last_message) - 1);
  g_last_message[sizeof(g_last_message) - 1] = '\0';
#endif

#if defined(ARDUINO)
  Serial.println(line);
#else
  std::printf("%s\n", line);
#endif
}

#if defined(UNIT_TEST)
// Poluchenie poslednego loga dlya testov.
const char* Logger::GetLastMessageForTests() {
  return g_last_message;
}

// Ochistka testovogo bufera loga.
void Logger::ClearLastMessageForTests() {
  g_last_message[0] = '\0';
}
#endif

}


