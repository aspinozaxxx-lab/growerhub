package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.PlantDtos;

@RestController
public class PlantsController {

    @GetMapping("/api/plant-groups")
    public List<PlantDtos.PlantGroupResponse> listPlantGroups() {
        throw todo();
    }

    @PostMapping("/api/plant-groups")
    public PlantDtos.PlantGroupResponse createPlantGroup(
            @RequestBody PlantDtos.PlantGroupCreateRequest request
    ) {
        throw todo();
    }

    @PatchMapping("/api/plant-groups/{group_id}")
    public PlantDtos.PlantGroupResponse updatePlantGroup(
            @PathVariable("group_id") Integer groupId,
            @RequestBody PlantDtos.PlantGroupUpdateRequest request
    ) {
        throw todo();
    }

    @DeleteMapping("/api/plant-groups/{group_id}")
    public CommonDtos.MessageResponse deletePlantGroup(
            @PathVariable("group_id") Integer groupId
    ) {
        throw todo();
    }

    @GetMapping("/api/plants")
    public List<PlantDtos.PlantResponse> listPlants() {
        throw todo();
    }

    @PostMapping("/api/plants")
    public PlantDtos.PlantResponse createPlant(
            @RequestBody PlantDtos.PlantCreateRequest request
    ) {
        throw todo();
    }

    @GetMapping("/api/plants/{plant_id}")
    public PlantDtos.PlantResponse getPlant(
            @PathVariable("plant_id") Integer plantId
    ) {
        throw todo();
    }

    @PatchMapping("/api/plants/{plant_id}")
    public PlantDtos.PlantResponse updatePlant(
            @PathVariable("plant_id") Integer plantId,
            @RequestBody PlantDtos.PlantUpdateRequest request
    ) {
        throw todo();
    }

    @DeleteMapping("/api/plants/{plant_id}")
    public CommonDtos.MessageResponse deletePlant(
            @PathVariable("plant_id") Integer plantId
    ) {
        throw todo();
    }

    @PostMapping("/api/plants/{plant_id}/devices/{device_id}")
    public PlantDtos.PlantResponse attachDevice(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("device_id") Integer deviceId
    ) {
        throw todo();
    }

    @DeleteMapping("/api/plants/{plant_id}/devices/{device_id}")
    public ResponseEntity<Void> detachDevice(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("device_id") Integer deviceId
    ) {
        throw todo();
    }

    @GetMapping("/api/plants/{plant_id}/journal")
    public List<PlantDtos.PlantJournalEntryResponse> listJournalEntries(
            @PathVariable("plant_id") Integer plantId
    ) {
        throw todo();
    }

    @GetMapping("/api/plants/{plant_id}/journal/export")
    public ResponseEntity<String> exportJournal(
            @PathVariable("plant_id") Integer plantId,
            @RequestParam(value = "format", defaultValue = "md") String format
    ) {
        throw todo();
    }

    @PostMapping("/api/plants/{plant_id}/journal")
    public PlantDtos.PlantJournalEntryResponse createJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @RequestBody PlantDtos.PlantJournalEntryCreateRequest request
    ) {
        throw todo();
    }

    @PatchMapping("/api/plants/{plant_id}/journal/{entry_id}")
    public PlantDtos.PlantJournalEntryResponse updateJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @RequestBody PlantDtos.PlantJournalEntryUpdateRequest request
    ) {
        throw todo();
    }

    @DeleteMapping("/api/plants/{plant_id}/journal/{entry_id}")
    public CommonDtos.MessageResponse deleteJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId
    ) {
        throw todo();
    }

    @GetMapping("/api/journal/photos/{photo_id}")
    public ResponseEntity<byte[]> getJournalPhoto(
            @PathVariable("photo_id") Integer photoId
    ) {
        throw todo();
    }

    @GetMapping("/api/admin/plants")
    public List<PlantDtos.AdminPlantResponse> listAdminPlants() {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
