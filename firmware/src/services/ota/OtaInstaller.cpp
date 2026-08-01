/*
 * Chto v faile: realizaciya HTTPS OTA dlya ESP32.
 * Rol v arhitekture: services.
 * Naznachenie: zagruzka binarnika s growerhub.ru, SHA-256 proverka i zapis v OTA partition.
 */

#include "services/ota/OtaInstaller.h"

#include <cctype>
#include <cstdio>
#include <cstring>

#include "config/MqttTlsTrust.h"
#include "config/HardwareProfile.h"
#include "util/Logger.h"

#if defined(ARDUINO)
#include <Arduino.h>
#include <HTTPClient.h>
#include <Update.h>
#include <WiFiClientSecure.h>
#include <mbedtls/sha256.h>
#endif

namespace Services {

namespace {

static const char* kFirmwareUrlPrefix = "https://growerhub.ru/firmware/";

bool IsValidSha256(const char* sha256) {
  if (!sha256 || std::strlen(sha256) != 64) {
    return false;
  }
  for (size_t i = 0; i < 64; ++i) {
    if (!std::isxdigit(static_cast<unsigned char>(sha256[i]))) {
      return false;
    }
  }
  return true;
}

bool IsAllowedFirmwareUrl(const char* url) {
  if (!url || std::strncmp(url, kFirmwareUrlPrefix, std::strlen(kFirmwareUrlPrefix)) != 0) {
    return false;
  }
  const char* filename = url + std::strlen(kFirmwareUrlPrefix);
  const size_t length = std::strlen(filename);
  if (length < 5 || length > 160 || std::strcmp(filename + length - 4, ".bin") != 0) {
    return false;
  }
  for (size_t i = 0; i < length; ++i) {
    const unsigned char value = static_cast<unsigned char>(filename[i]);
    if (!std::isalnum(value) && value != '.' && value != '_' && value != '-') {
      return false;
    }
  }
  char expected_suffix[96];
  const int suffix_length = std::snprintf(
      expected_suffix,
      sizeof(expected_suffix),
      ".%s.bin",
      Config::GetHardwareProfile().name);
  if (suffix_length <= 0 || static_cast<size_t>(suffix_length) >= sizeof(expected_suffix)
      || length <= static_cast<size_t>(suffix_length)) {
    return false;
  }
  return std::strcmp(filename + length - static_cast<size_t>(suffix_length), expected_suffix) == 0;
}

#if defined(ARDUINO)
void DigestToHex(const unsigned char digest[32], char out[65]) {
  for (size_t i = 0; i < 32; ++i) {
    std::snprintf(out + i * 2, 3, "%02x", digest[i]);
  }
  out[64] = '\0';
}
#endif

}

const char* OtaInstallResultReason(OtaInstallResult result) {
  switch (result) {
    case OtaInstallResult::kOk:
      return "ok";
    case OtaInstallResult::kInvalidRequest:
      return "invalid_ota_request";
    case OtaInstallResult::kTlsOrNetworkFailed:
      return "tls_or_network_failed";
    case OtaInstallResult::kHttpFailed:
      return "firmware_http_failed";
    case OtaInstallResult::kBeginFailed:
      return "ota_begin_failed";
    case OtaInstallResult::kDownloadFailed:
      return "firmware_download_failed";
    case OtaInstallResult::kWriteFailed:
      return "firmware_write_failed";
    case OtaInstallResult::kSha256Mismatch:
      return "firmware_sha256_mismatch";
    case OtaInstallResult::kFinalizeFailed:
      return "ota_finalize_failed";
    case OtaInstallResult::kUnavailable:
    default:
      return "ota_unavailable";
  }
}

OtaInstallResult PlatformOtaInstaller::Install(const char* url, const char* sha256) {
  if (!IsAllowedFirmwareUrl(url) || !IsValidSha256(sha256)) {
    return OtaInstallResult::kInvalidRequest;
  }

#if !defined(ARDUINO)
  (void)url;
  (void)sha256;
  return OtaInstallResult::kUnavailable;
#else
  WiFiClientSecure client;
  client.setCACert(Config::kIsrgRootX1);

  HTTPClient http;
  http.setConnectTimeout(15000);
  http.setTimeout(30000);
  if (!http.begin(client, url)) {
    return OtaInstallResult::kTlsOrNetworkFailed;
  }

  const int response_code = http.GET();
  if (response_code <= 0) {
    http.end();
    return OtaInstallResult::kTlsOrNetworkFailed;
  }
  if (response_code != HTTP_CODE_OK) {
    http.end();
    return OtaInstallResult::kHttpFailed;
  }

  const int content_length = http.getSize();
  if (content_length <= 0 || !Update.begin(static_cast<size_t>(content_length), U_FLASH)) {
    http.end();
    return OtaInstallResult::kBeginFailed;
  }

  mbedtls_sha256_context digest_context;
  mbedtls_sha256_init(&digest_context);
  if (mbedtls_sha256_starts_ret(&digest_context, 0) != 0) {
    mbedtls_sha256_free(&digest_context);
    Update.abort();
    http.end();
    return OtaInstallResult::kBeginFailed;
  }

  WiFiClient* stream = http.getStreamPtr();
  uint8_t buffer[2048];
  int remaining = content_length;
  uint32_t last_data_ms = millis();
  OtaInstallResult result = OtaInstallResult::kOk;

  while (remaining > 0) {
    const size_t available = stream->available();
    if (available == 0) {
      if (!http.connected() || millis() - last_data_ms > 30000) {
        result = OtaInstallResult::kDownloadFailed;
        break;
      }
      delay(1);
      continue;
    }

    size_t chunk = available;
    if (chunk > sizeof(buffer)) {
      chunk = sizeof(buffer);
    }
    if (chunk > static_cast<size_t>(remaining)) {
      chunk = static_cast<size_t>(remaining);
    }
    const size_t read = stream->readBytes(buffer, chunk);
    if (read == 0) {
      result = OtaInstallResult::kDownloadFailed;
      break;
    }
    last_data_ms = millis();
    if (mbedtls_sha256_update_ret(&digest_context, buffer, read) != 0) {
      result = OtaInstallResult::kDownloadFailed;
      break;
    }
    if (Update.write(buffer, read) != read) {
      result = OtaInstallResult::kWriteFailed;
      break;
    }
    remaining -= static_cast<int>(read);
    delay(0);
  }

  unsigned char digest[32]{};
  if (result == OtaInstallResult::kOk
      && mbedtls_sha256_finish_ret(&digest_context, digest) != 0) {
    result = OtaInstallResult::kDownloadFailed;
  }
  mbedtls_sha256_free(&digest_context);

  if (result == OtaInstallResult::kOk) {
    char actual_sha256[65];
    DigestToHex(digest, actual_sha256);
    if (strcasecmp(actual_sha256, sha256) != 0) {
      result = OtaInstallResult::kSha256Mismatch;
    }
  }

  if (result == OtaInstallResult::kOk) {
    if (!Update.end(true)) {
      result = OtaInstallResult::kFinalizeFailed;
    }
  } else {
    Update.abort();
  }
  http.end();

  char log_buffer[128];
  std::snprintf(log_buffer,
                sizeof(log_buffer),
                "[OTA] install result=%s bytes=%d",
                OtaInstallResultReason(result),
                content_length - remaining);
  Util::Logger::Info(log_buffer);
  return result;
#endif
}

}
