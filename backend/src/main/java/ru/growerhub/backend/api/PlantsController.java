
package ru.growerhub.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.DeviceDtos;
import ru.growerhub.backend.api.dto.PlantDtos;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.PlantEntity;
import ru.growerhub.backend.db.PlantGroupEntity;
import ru.growerhub.backend.db.PlantGroupRepository;
import ru.growerhub.backend.db.PlantJournalEntryEntity;
import ru.growerhub.backend.db.PlantJournalEntryRepository;
import ru.growerhub.backend.db.PlantJournalPhotoEntity;
import ru.growerhub.backend.db.PlantJournalPhotoRepository;
import ru.growerhub.backend.db.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.db.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.db.PlantRepository;
import ru.growerhub.backend.device.DeviceService;
import ru.growerhub.backend.user.UserEntity;

@RestController
@Validated
public class PlantsController {
    private static final Set<String> JOURNAL_TYPES = Set.of(
            "watering",
            "feeding",
            "note",
            "photo",
            "other"
    );

    private final PlantGroupRepository plantGroupRepository;
    private final PlantRepository plantRepository;
    private final PlantDeviceRepository plantDeviceRepository;
    private final PlantJournalEntryRepository plantJournalEntryRepository;
    private final PlantJournalPhotoRepository plantJournalPhotoRepository;
    private final PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;

    public PlantsController(
            PlantGroupRepository plantGroupRepository,
            PlantRepository plantRepository,
            PlantDeviceRepository plantDeviceRepository,
            PlantJournalEntryRepository plantJournalEntryRepository,
            PlantJournalPhotoRepository plantJournalPhotoRepository,
            PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository,
            DeviceRepository deviceRepository,
            DeviceService deviceService
    ) {
        this.plantGroupRepository = plantGroupRepository;
        this.plantRepository = plantRepository;
        this.plantDeviceRepository = plantDeviceRepository;
        this.plantJournalEntryRepository = plantJournalEntryRepository;
        this.plantJournalPhotoRepository = plantJournalPhotoRepository;
        this.plantJournalWateringDetailsRepository = plantJournalWateringDetailsRepository;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
    }

    @GetMapping("/api/plant-groups")
    public List<PlantDtos.PlantGroupResponse> listPlantGroups(
            @AuthenticationPrincipal UserEntity user
    ) {
        List<PlantGroupEntity> groups = plantGroupRepository.findAllByUser_Id(user.getId());
        return groups.stream().map(this::toGroupResponse).toList();
    }

    @PostMapping("/api/plant-groups")
    @Transactional
    public PlantDtos.PlantGroupResponse createPlantGroup(
            @Valid @RequestBody PlantDtos.PlantGroupCreateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PlantGroupEntity group = PlantGroupEntity.create();
        group.setName(request.name());
        group.setUser(user);
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        plantGroupRepository.save(group);
        return toGroupResponse(group);
    }

    @PatchMapping("/api/plant-groups/{group_id}")
    @Transactional
    public PlantDtos.PlantGroupResponse updatePlantGroup(
            @PathVariable("group_id") Integer groupId,
            @Valid @RequestBody PlantDtos.PlantGroupUpdateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantGroupEntity group = plantGroupRepository.findByIdAndUser_Id(groupId, user.getId()).orElse(null);
        if (group == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "gruppa ne najdena");
        }
        group.setName(request.name());
        group.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantGroupRepository.save(group);
        return toGroupResponse(group);
    }

    @DeleteMapping("/api/plant-groups/{group_id}")
    @Transactional
    public CommonDtos.MessageResponse deletePlantGroup(
            @PathVariable("group_id") Integer groupId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantGroupEntity group = plantGroupRepository.findByIdAndUser_Id(groupId, user.getId()).orElse(null);
        if (group == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "gruppa ne najdena");
        }
        List<PlantEntity> plants = plantRepository.findAllByUser_IdAndPlantGroup_Id(user.getId(), groupId);
        for (PlantEntity plant : plants) {
            plant.setPlantGroup(null);
        }
        plantRepository.saveAll(plants);
        plantGroupRepository.delete(group);
        return new CommonDtos.MessageResponse("group deleted");
    }

    @GetMapping("/api/plants")
    public List<PlantDtos.PlantResponse> listPlants(
            @AuthenticationPrincipal UserEntity user
    ) {
        List<PlantEntity> plants = plantRepository.findAllByUser_Id(user.getId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        List<PlantDtos.PlantResponse> responses = new ArrayList<>();
        for (PlantEntity plant : plants) {
            responses.add(toPlantResponse(plant, user, now, onlineWindow));
        }
        return responses;
    }

    @PostMapping("/api/plants")
    @Transactional
    public PlantDtos.PlantResponse createPlant(
            @Valid @RequestBody PlantDtos.PlantCreateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime plantedAt = request.plantedAt() != null ? request.plantedAt() : now;
        PlantEntity plant = PlantEntity.create();
        plant.setName(request.name());
        plant.setPlantedAt(plantedAt);
        plant.setUser(user);
        plant.setPlantGroup(resolvePlantGroup(request.plantGroupId()));
        plant.setPlantType(request.plantType());
        plant.setStrain(request.strain());
        plant.setGrowthStage(request.growthStage());
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        plantRepository.save(plant);

        Duration onlineWindow = Duration.ofMinutes(3);
        return toPlantResponse(plant, user, now, onlineWindow);
    }

    @GetMapping("/api/plants/{plant_id}")
    public PlantDtos.PlantResponse getPlant(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        return toPlantResponse(plant, user, now, onlineWindow);
    }
    @PatchMapping("/api/plants/{plant_id}")
    @Transactional
    public PlantDtos.PlantResponse updatePlant(
            @PathVariable("plant_id") Integer plantId,
            @RequestBody JsonNode request,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        boolean changed = false;

        if (request.has("name")) {
            plant.setName(textValue(request.get("name")));
            changed = true;
        }
        if (request.has("planted_at")) {
            plant.setPlantedAt(parseDateValue(request.get("planted_at"), "planted_at"));
            changed = true;
        }
        if (request.has("plant_group_id")) {
            Integer groupId = intValue(request.get("plant_group_id"), "plant_group_id");
            plant.setPlantGroup(groupId != null ? resolvePlantGroup(groupId) : null);
            changed = true;
        }
        if (request.has("plant_type")) {
            plant.setPlantType(textValue(request.get("plant_type")));
            changed = true;
        }
        if (request.has("strain")) {
            plant.setStrain(textValue(request.get("strain")));
            changed = true;
        }
        if (request.has("growth_stage")) {
            plant.setGrowthStage(textValue(request.get("growth_stage")));
            changed = true;
        }

        if (changed) {
            plant.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            plantRepository.save(plant);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        return toPlantResponse(plant, user, now, onlineWindow);
    }

    @DeleteMapping("/api/plants/{plant_id}")
    @Transactional
    public CommonDtos.MessageResponse deletePlant(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        plantDeviceRepository.deleteAllByPlant_Id(plant.getId());
        plantJournalEntryRepository.deleteAllByPlant_Id(plant.getId());
        plantRepository.delete(plant);
        return new CommonDtos.MessageResponse("plant deleted");
    }

    @PostMapping("/api/plants/{plant_id}/devices/{device_id}")
    @Transactional
    public PlantDtos.PlantResponse attachDevice(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("device_id") Integer deviceId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        DeviceEntity device = requireUserDevice(deviceId, user);
        PlantDeviceEntity existing = plantDeviceRepository.findByPlant_IdAndDevice_Id(plant.getId(), device.getId());
        if (existing == null) {
            PlantDeviceEntity link = PlantDeviceEntity.create();
            link.setPlant(plant);
            link.setDevice(device);
            plantDeviceRepository.save(link);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        return toPlantResponse(plant, user, now, onlineWindow);
    }

    @DeleteMapping("/api/plants/{plant_id}/devices/{device_id}")
    @Transactional
    public ResponseEntity<Void> detachDevice(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("device_id") Integer deviceId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireUserPlant(plantId, user);
        requireUserDevice(deviceId, user);
        plantDeviceRepository.deleteByPlant_IdAndDevice_Id(plantId, deviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/plants/{plant_id}/journal")
    public List<PlantDtos.PlantJournalEntryResponse> listJournalEntries(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireUserPlant(plantId, user);
        List<PlantJournalEntryEntity> entries =
                plantJournalEntryRepository.findAllByPlant_IdOrderByEventAtDesc(plantId);
        List<PlantDtos.PlantJournalEntryResponse> responses = new ArrayList<>();
        for (PlantJournalEntryEntity entry : entries) {
            responses.add(toJournalEntryResponse(entry));
        }
        return responses;
    }

    @GetMapping("/api/plants/{plant_id}/journal/export")
    public ResponseEntity<String> exportJournal(
            @PathVariable("plant_id") Integer plantId,
            @RequestParam(value = "format", defaultValue = "md") String format,
            @AuthenticationPrincipal UserEntity user
    ) {
        if (!"md".equals(format)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "podderzhivaetsya tolko format=md");
        }
        PlantEntity plant = requireUserPlant(plantId, user);
        List<PlantJournalEntryEntity> entries =
                plantJournalEntryRepository.findAllByPlant_IdOrderByEventAtAsc(plantId);
        String markdown = buildJournalMarkdown(plant, entries);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plant_journal_" + plantId + ".md\"");
        headers.set(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=utf-8");
        return new ResponseEntity<>(markdown, headers, HttpStatus.OK);
    }

    @PostMapping("/api/plants/{plant_id}/journal")
    @Transactional
    public PlantDtos.PlantJournalEntryResponse createJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @Valid @RequestBody PlantDtos.PlantJournalEntryCreateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        validateJournalType(request.type());
        requireUserPlant(plantId, user);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime eventAt = request.eventAt() != null ? request.eventAt() : now;
        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlant(resolvePlantReference(plantId));
        entry.setUser(user);
        entry.setType(request.type());
        entry.setText(request.text());
        entry.setEventAt(eventAt);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        plantJournalEntryRepository.save(entry);

        List<String> urls = request.photoUrls();
        if (urls != null && !urls.isEmpty()) {
            List<PlantJournalPhotoEntity> photos = new ArrayList<>();
            for (String url : urls) {
                PlantJournalPhotoEntity photo = PlantJournalPhotoEntity.create();
                photo.setJournalEntry(entry);
                photo.setUrl(url);
                photos.add(photo);
            }
            plantJournalPhotoRepository.saveAll(photos);
        }

        return toJournalEntryResponse(entry);
    }

    @PatchMapping("/api/plants/{plant_id}/journal/{entry_id}")
    @Transactional
    public PlantDtos.PlantJournalEntryResponse updateJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @RequestBody JsonNode request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireUserPlant(plantId, user);
        PlantJournalEntryEntity entry = plantJournalEntryRepository
                .findByIdAndPlant_IdAndUser_Id(entryId, plantId, user.getId())
                .orElse(null);
        if (entry == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "zapis' ne najdena");
        }

        if (request.has("type")) {
            String type = textValue(request.get("type"));
            if (type != null) {
                validateJournalType(type);
            }
            entry.setType(type);
        }
        if (request.has("text")) {
            entry.setText(textValue(request.get("text")));
        }
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantJournalEntryRepository.save(entry);
        return toJournalEntryResponse(entry);
    }

    @DeleteMapping("/api/plants/{plant_id}/journal/{entry_id}")
    @Transactional
    public CommonDtos.MessageResponse deleteJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireUserPlant(plantId, user);
        PlantJournalEntryEntity entry = plantJournalEntryRepository
                .findByIdAndPlant_IdAndUser_Id(entryId, plantId, user.getId())
                .orElse(null);
        if (entry == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "zapis' ne najdena");
        }
        plantJournalEntryRepository.delete(entry);
        return new CommonDtos.MessageResponse("entry deleted");
    }
    @GetMapping("/api/journal/photos/{photo_id}")
    public ResponseEntity<byte[]> getJournalPhoto(
            @PathVariable("photo_id") Integer photoId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantJournalPhotoEntity photo = plantJournalPhotoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "foto ne najdeno ili nedostupno");
        }
        PlantJournalEntryEntity entry = photo.getJournalEntry();
        PlantEntity plant = entry != null ? entry.getPlant() : null;
        UserEntity owner = plant != null ? plant.getUser() : null;
        if (owner == null || !owner.getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "foto ne najdeno ili nedostupno");
        }
        byte[] data = photo.getData();
        if (data == null || data.length == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "binarnye dannye dlya etogo foto otsutstvuyut");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(
                HttpHeaders.CONTENT_TYPE,
                photo.getContentType() != null ? photo.getContentType() : "application/octet-stream"
        );
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @GetMapping("/api/admin/plants")
    public List<PlantDtos.AdminPlantResponse> listAdminPlants(
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        List<PlantEntity> plants = plantRepository.findAll();
        List<PlantDtos.AdminPlantResponse> responses = new ArrayList<>();
        for (PlantEntity plant : plants) {
            UserEntity owner = plant.getUser();
            PlantGroupEntity group = plant.getPlantGroup();
            responses.add(new PlantDtos.AdminPlantResponse(
                    plant.getId(),
                    plant.getName(),
                    owner != null ? owner.getEmail() : null,
                    owner != null ? owner.getUsername() : null,
                    owner != null ? owner.getId() : null,
                    group != null ? group.getName() : null
            ));
        }
        return responses;
    }

    private PlantEntity requireUserPlant(Integer plantId, UserEntity user) {
        PlantEntity plant = plantRepository.findByIdAndUser_Id(plantId, user.getId()).orElse(null);
        if (plant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "rastenie ne najdeno");
        }
        return plant;
    }

    private DeviceEntity requireUserDevice(Integer deviceId, UserEntity user) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || device.getUser() == null || !device.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ustrojstvo ne najdeno");
        }
        return device;
    }

    private PlantGroupEntity resolvePlantGroup(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return plantGroupRepository.getReferenceById(groupId);
    }

    private PlantEntity resolvePlantReference(Integer plantId) {
        return plantRepository.getReferenceById(plantId);
    }

    private PlantDtos.PlantGroupResponse toGroupResponse(PlantGroupEntity group) {
        UserEntity owner = group.getUser();
        return new PlantDtos.PlantGroupResponse(
                group.getId(),
                group.getName(),
                owner != null ? owner.getId() : null
        );
    }

    private PlantDtos.PlantResponse toPlantResponse(
            PlantEntity plant,
            UserEntity user,
            LocalDateTime now,
            Duration onlineWindow
    ) {
        PlantGroupEntity group = plant.getPlantGroup();
        PlantDtos.PlantGroupResponse groupResponse = null;
        if (group != null) {
            UserEntity owner = group.getUser();
            groupResponse = new PlantDtos.PlantGroupResponse(
                    group.getId(),
                    group.getName(),
                    owner != null ? owner.getId() : null
            );
        }

        List<PlantDeviceEntity> links = plantDeviceRepository.findAllByPlant_Id(plant.getId());
        List<DeviceDtos.DeviceResponse> devices = new ArrayList<>();
        for (PlantDeviceEntity link : links) {
            DeviceEntity device = link.getDevice();
            if (device == null) {
                continue;
            }
            DeviceDtos.DeviceResponse response = deviceService.buildDeviceResponse(device.getDeviceId(), user.getId());
            if (response != null) {
                devices.add(response);
            }
        }

        UserEntity owner = plant.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new PlantDtos.PlantResponse(
                plant.getId(),
                plant.getName(),
                plant.getPlantedAt(),
                plant.getPlantType(),
                plant.getStrain(),
                plant.getGrowthStage(),
                ownerId,
                groupResponse,
                devices
        );
    }

    private PlantDtos.PlantJournalEntryResponse toJournalEntryResponse(PlantJournalEntryEntity entry) {
        List<PlantJournalPhotoEntity> photos =
                plantJournalPhotoRepository.findAllByJournalEntry_Id(entry.getId());
        List<PlantDtos.PlantJournalPhotoResponse> photoResponses = new ArrayList<>();
        for (PlantJournalPhotoEntity photo : photos) {
            boolean hasData = photo.getData() != null && photo.getData().length > 0;
            photoResponses.add(new PlantDtos.PlantJournalPhotoResponse(
                    photo.getId(),
                    photo.getUrl(),
                    photo.getCaption(),
                    hasData
            ));
        }

        PlantDtos.PlantJournalWateringDetailsResponse detailsResponse = null;
        if ("watering".equals(entry.getType())) {
            PlantJournalWateringDetailsEntity details = plantJournalWateringDetailsRepository
                    .findByJournalEntry_Id(entry.getId())
                    .orElse(null);
            if (details != null) {
                detailsResponse = new PlantDtos.PlantJournalWateringDetailsResponse(
                        details.getWaterVolumeL(),
                        details.getDurationS(),
                        details.getPh(),
                        details.getFertilizersPerLiter()
                );
            }
        }

        UserEntity owner = entry.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new PlantDtos.PlantJournalEntryResponse(
                entry.getId(),
                entry.getPlant().getId(),
                ownerId,
                entry.getType(),
                entry.getText(),
                entry.getEventAt(),
                entry.getCreatedAt(),
                photoResponses,
                detailsResponse
        );
    }

    private String buildJournalMarkdown(PlantEntity plant, List<PlantJournalEntryEntity> entries) {
        LocalDate plantedDate = plant.getPlantedAt() != null
                ? plant.getPlantedAt().toLocalDate()
                : LocalDate.now();
        long ageDays = java.time.temporal.ChronoUnit.DAYS.between(plantedDate, LocalDate.now());
        List<String> lines = new ArrayList<>();
        lines.add("# Журнал растения");
        lines.add("");
        lines.add("Название: " + plant.getName());
        lines.add("Дата посадки: " + plantedDate);
        lines.add("Текущий возраст: " + Math.max(ageDays, 0) + " дней");
        lines.add("");

        entries.sort(Comparator.comparing(PlantJournalEntryEntity::getEventAt));
        LocalDate currentDay = null;
        for (PlantJournalEntryEntity entry : entries) {
            LocalDate entryDay = entry.getEventAt().toLocalDate();
            if (!entryDay.equals(currentDay)) {
                if (currentDay != null) {
                    lines.add("");
                }
                lines.add("## " + entryDay);
                currentDay = entryDay;
            }
            String timePart = entry.getEventAt().toLocalTime().withSecond(0).withNano(0).toString();
            if (timePart.length() > 5) {
                timePart = timePart.substring(0, 5);
            }
            String icon = "??";
            String label = "Наблюдение";
            String text = entry.getText() != null ? entry.getText() : "";

            if ("watering".equals(entry.getType())) {
                icon = "??";
                label = "Полив";
                PlantJournalWateringDetailsEntity details = plantJournalWateringDetailsRepository
                        .findByJournalEntry_Id(entry.getId())
                        .orElse(null);
                String detailsText = buildWateringText(details);
                if (!detailsText.isEmpty()) {
                    text = detailsText;
                }
            } else if ("feeding".equals(entry.getType())) {
                icon = "??";
                label = "Уход";
            } else if ("photo".equals(entry.getType())) {
                icon = "??";
                label = "Фото";
                if (text.isEmpty()) {
                    text = "Фото";
                }
            }

            String textSuffix = text.isEmpty() ? "" : ": " + text;
            lines.add("- " + timePart + " " + icon + " " + label + textSuffix);
        }

        String payload = String.join("\n", lines).trim();
        return payload + "\n";
    }

    private String buildWateringText(PlantJournalWateringDetailsEntity details) {
        if (details == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String volume = formatVolumeLiters(details.getWaterVolumeL());
        if (volume != null) {
            parts.add(volume);
        }
        if (details.getFertilizersPerLiter() != null && !details.getFertilizersPerLiter().isEmpty()) {
            parts.add("udobreniya: " + details.getFertilizersPerLiter());
        }
        return String.join("; ", parts);
    }

    private String formatVolumeLiters(Double value) {
        if (value == null) {
            return null;
        }
        String formatted = String.format(Locale.US, "%.2f", value);
        if (formatted.contains(".")) {
            while (formatted.endsWith("0")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
            if (formatted.endsWith(".")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
        }
        return formatted.replace(".", ",") + " л";
    }

    private void validateJournalType(String type) {
        if (!JOURNAL_TYPES.contains(type)) {
            ApiValidationErrorItem item = new ApiValidationErrorItem(
                    List.of("body", "type"),
                    "Input should be 'watering' or 'feeding' or 'note' or 'photo' or 'other'",
                    "literal_error"
            );
            throw new ApiValidationException(new ApiValidationError(List.of(item)));
        }
    }

    private LocalDateTime parseDateValue(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalidFieldValue(fieldName);
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            throw invalidFieldValue(fieldName);
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException inner) {
                throw invalidFieldValue(fieldName);
            }
        }
    }

    private Integer intValue(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw invalidFieldValue(fieldName);
        }
        return node.intValue();
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private ApiValidationException invalidFieldValue(String fieldName) {
        ApiValidationErrorItem item = new ApiValidationErrorItem(
                List.of("body", fieldName),
                "Invalid value",
                "value_error"
        );
        return new ApiValidationException(new ApiValidationError(List.of(item)));
    }

    private void requireAdmin(UserEntity user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }

}
