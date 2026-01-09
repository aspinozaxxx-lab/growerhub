package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.api.dto.FirmwareDtos;
import ru.growerhub.backend.firmware.FirmwareCheckResult;
import ru.growerhub.backend.firmware.FirmwareFacade;
import ru.growerhub.backend.firmware.FirmwareTriggerResult;
import ru.growerhub.backend.firmware.FirmwareUploadResult;
import ru.growerhub.backend.firmware.FirmwareVersionInfo;

@RestController
@Validated
public class FirmwareController {
    private final FirmwareFacade firmwareFacade;

    public FirmwareController(FirmwareFacade firmwareFacade) {
        this.firmwareFacade = firmwareFacade;
    }

    @GetMapping("/api/device/{device_id}/firmware")
    public FirmwareDtos.FirmwareCheckResponse checkFirmwareUpdate(
            @PathVariable("device_id") String deviceId
    ) {
        FirmwareCheckResult result = firmwareFacade.checkFirmwareUpdate(deviceId);
        return new FirmwareDtos.FirmwareCheckResponse(
                result.updateAvailable(),
                result.version(),
                result.url()
        );
    }

    @PostMapping("/api/upload-firmware")
    public ResponseEntity<FirmwareDtos.UploadFirmwareResponse> uploadFirmware(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version
    ) {
        FirmwareUploadResult result = firmwareFacade.uploadFirmware(file, version);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FirmwareDtos.UploadFirmwareResponse(
                        result.status(),
                        result.version(),
                        result.path()
                ));
    }

    @PostMapping("/api/device/{device_id}/trigger-update")
    public ResponseEntity<FirmwareDtos.TriggerFirmwareUpdateResponse> triggerUpdate(
            @PathVariable("device_id") String deviceId,
            @RequestBody FirmwareDtos.TriggerFirmwareUpdateRequest request
    ) {
        String version = resolveVersion(request);
        FirmwareTriggerResult result = firmwareFacade.triggerUpdate(deviceId, version);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new FirmwareDtos.TriggerFirmwareUpdateResponse(
                        result.status(),
                        result.version(),
                        result.url(),
                        result.sha256()
                ));
    }

    @GetMapping("/api/firmware/versions")
    public ResponseEntity<List<FirmwareDtos.FirmwareVersionResponse>> listFirmwareVersions() {
        List<FirmwareVersionInfo> versions = firmwareFacade.listFirmwareVersions();
        List<FirmwareDtos.FirmwareVersionResponse> response = versions.stream()
                .map(this::toVersionResponse)
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    private FirmwareDtos.FirmwareVersionResponse toVersionResponse(FirmwareVersionInfo info) {
        String mtime = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(info.mtime().atOffset(java.time.ZoneOffset.UTC));
        return new FirmwareDtos.FirmwareVersionResponse(
                info.version(),
                info.size(),
                info.sha256(),
                mtime
        );
    }

    private String resolveVersion(FirmwareDtos.TriggerFirmwareUpdateRequest request) {
        String candidate = request != null ? request.version() : null;
        if (candidate == null || candidate.isBlank()) {
            candidate = request != null ? request.firmwareVersion() : null;
        }
        if (candidate == null || candidate.isBlank()) {
            ApiValidationErrorItem item = new ApiValidationErrorItem(
                    List.of("body", "__root__"),
                    "version required",
                    "value_error"
            );
            throw new ApiValidationException(new ApiValidationError(List.of(item)));
        }
        return candidate;
    }
}
