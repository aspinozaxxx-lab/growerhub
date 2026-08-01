/*
 * Chto v faile: kontrakt ustanovki OTA i ESP32 realizaciya.
 * Rol v arhitekture: services.
 * Naznachenie: bezopasnaya zagruzka i proverka binarnika proshivki.
 */

#pragma once

namespace Services {

enum class OtaInstallResult {
  kOk = 0,
  kInvalidRequest,
  kTlsOrNetworkFailed,
  kHttpFailed,
  kBeginFailed,
  kDownloadFailed,
  kWriteFailed,
  kSha256Mismatch,
  kFinalizeFailed,
  kUnavailable
};

const char* OtaInstallResultReason(OtaInstallResult result);

class OtaInstaller {
 public:
  virtual ~OtaInstaller() {}
  virtual OtaInstallResult Install(const char* url, const char* sha256) = 0;
};

class PlatformOtaInstaller : public OtaInstaller {
 public:
  OtaInstallResult Install(const char* url, const char* sha256) override;
};

}
