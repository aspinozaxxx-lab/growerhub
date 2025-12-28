/*
 * Chto v faile: obyavleniya parsera komand MQTT i formirovaniya ack.
 * Rol v arhitekture: util.
 * Naznachenie: publichnyi API i tipy dlya sloya util.
 * Soderzhit: klassy, struktury i publichnye metody.
 */

#pragma once

#include <cstddef>
#include <cstdint>

namespace Util {

// Maksimalnaya dlina correlation_id.
static const size_t kCorrelationIdMax = 64;

enum class CommandType : uint8_t {
  kUnknown = 0, // Neizvestnaya komanda.
  kPumpStart = 1, // Start nasosa.
  kPumpStop = 2, // Stop nasosa.
  kReboot = 3, // Reboot ustroistva.
  kCfgSync = 4 // Zapros sinhronizacii konfiguracii.
};

enum class ParseError : uint8_t {
  kNone = 0, // Oshibok net.
  kInvalidJson = 1, // Nekorrektnyi JSON.
  kTypeMissing = 2, // Ne ukazan tip komandy.
  kDurationMissingOrInvalid = 3, // Net duration_s ili ona nekorrektna.
  kUnsupportedCommand = 4 // Ne podderzhivaemaya komanda.
};

struct Command {
  // Tip raspsechennoy komandy.
  CommandType type;
  // Dlitelnost dlya komandy start.
  uint32_t duration_s;
  // Correlation ID komandy.
  char correlation_id[kCorrelationIdMax];
};

/**
 * Parser MQTT komandy iz JSON payload.
 * @param json Vhodnaya JSON stroka s komandoy.
 * @param out Vyhodnaya struktura komandy.
 * @param error Kod oshibki parsa.
 */
bool ParseCommand(const char* json, Command& out, ParseError& error);
/**
 * Vozvrashaet tekst dlya koda oshibki parsa.
 * @param error Kod oshibki parsa.
 */
const char* ParseErrorReason(ParseError error);

/**
 * Stroit ACK s statusom.
 * @param correlation_id Correlation ID komandy.
 * @param result Result status (accepted/declined).
 * @param status Tekushchee sostoyanie (running/idle).
 * @param out Bufer dlya JSON stroki.
 * @param out_size Razmer bufera v baytah.
 */
bool BuildAckStatus(const char* correlation_id, const char* result, const char* status,
                    char* out, size_t out_size);
/**
 * Stroit ACK s oshibkoy.
 * @param correlation_id Correlation ID komandy.
 * @param reason Tekst prichiny oshibki.
 * @param out Bufer dlya JSON stroki.
 * @param out_size Razmer bufera v baytah.
 */
bool BuildAckError(const char* correlation_id, const char* reason, char* out, size_t out_size);

}
