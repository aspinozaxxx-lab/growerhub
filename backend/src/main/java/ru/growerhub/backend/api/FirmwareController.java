package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.api.dto.FirmwareDtos;

@RestController
public class FirmwareController {

    @GetMapping("/api/device/{device_id}/firmware")
    public FirmwareDtos.FirmwareCheckResponse checkFirmware(
            @PathVariable("device_id") String deviceId
    ) {
        throw todo();
    }

    @PostMapping(
            value = "/api/upload-firmware",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public FirmwareDtos.UploadFirmwareResponse uploadFirmware(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version
    ) {
        throw todo();
    }

    @PostMapping("/api/device/{device_id}/trigger-update")
    public FirmwareDtos.TriggerFirmwareUpdateResponse triggerUpdate(
            @PathVariable("device_id") String deviceId,
            @RequestBody FirmwareDtos.TriggerFirmwareUpdateRequest request
    ) {
        throw todo();
    }

    @GetMapping("/api/firmware/versions")
    public List<FirmwareDtos.FirmwareVersionResponse> listFirmwareVersions() {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
