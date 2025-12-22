package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.DeviceDtos;

@RestController
public class DevicesController {

    @PostMapping("/api/device/{device_id}/status")
    public CommonDtos.MessageResponse updateDeviceStatus(
            @PathVariable("device_id") String deviceId,
            @RequestBody DeviceDtos.DeviceStatusRequest request
    ) {
        throw todo();
    }

    @GetMapping("/api/device/{device_id}/settings")
    public DeviceDtos.DeviceSettingsResponse getDeviceSettings(
            @PathVariable("device_id") String deviceId
    ) {
        throw todo();
    }

    @PutMapping("/api/device/{device_id}/settings")
    public CommonDtos.MessageResponse updateDeviceSettings(
            @PathVariable("device_id") String deviceId,
            @RequestBody DeviceDtos.DeviceSettingsRequest request
    ) {
        throw todo();
    }

    @GetMapping("/api/devices")
    public List<DeviceDtos.DeviceResponse> listDevices() {
        throw todo();
    }

    @GetMapping("/api/devices/my")
    public List<DeviceDtos.DeviceResponse> listMyDevices() {
        throw todo();
    }

    @GetMapping("/api/admin/devices")
    public List<DeviceDtos.AdminDeviceResponse> listAdminDevices() {
        throw todo();
    }

    @PostMapping("/api/devices/assign-to-me")
    public DeviceDtos.DeviceResponse assignToMe(
            @RequestBody DeviceDtos.AssignToMeRequest request
    ) {
        throw todo();
    }

    @PostMapping("/api/devices/{device_id}/unassign")
    public DeviceDtos.DeviceResponse unassignDevice(
            @PathVariable("device_id") Integer deviceId
    ) {
        throw todo();
    }

    @PostMapping("/api/admin/devices/{device_id}/assign")
    public DeviceDtos.AdminDeviceResponse adminAssignDevice(
            @PathVariable("device_id") Integer deviceId,
            @RequestBody DeviceDtos.AdminAssignRequest request
    ) {
        throw todo();
    }

    @PostMapping("/api/admin/devices/{device_id}/unassign")
    public DeviceDtos.AdminDeviceResponse adminUnassignDevice(
            @PathVariable("device_id") Integer deviceId
    ) {
        throw todo();
    }

    @DeleteMapping("/api/device/{device_id}")
    public CommonDtos.MessageResponse deleteDevice(
            @PathVariable("device_id") String deviceId
    ) {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
