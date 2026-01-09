package ru.growerhub.backend.plant;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.ApiValidationError;
import ru.growerhub.backend.api.ApiValidationErrorItem;
import ru.growerhub.backend.api.ApiValidationException;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.DeviceDtos;
import ru.growerhub.backend.api.dto.PlantDtos;
import ru.growerhub.backend.journal.JournalService;
import ru.growerhub.backend.pump.PumpQueryService;
import ru.growerhub.backend.pump.PumpView;
import ru.growerhub.backend.sensor.SensorQueryService;
import ru.growerhub.backend.sensor.SensorView;
import ru.growerhub.backend.user.UserEntity;

@RestController
@Validated
public class PlantController {
    private final PlantGroupRepository plantGroupRepository;
    private final PlantRepository plantRepository;
    private final SensorQueryService sensorQueryService;
    private final PumpQueryService pumpQueryService;
    private final JournalService journalService;

    public PlantController(
            PlantGroupRepository plantGroupRepository,
            PlantRepository plantRepository,
            SensorQueryService sensorQueryService,
            PumpQueryService pumpQueryService,
            JournalService journalService
    ) {
        this.plantGroupRepository = plantGroupRepository;
        this.plantRepository = plantRepository;
        this.sensorQueryService = sensorQueryService;
        this.pumpQueryService = pumpQueryService;
        this.journalService = journalService;
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
            throw new ApiException(HttpStatus.NOT_FOUND, "gruppa ne naidena");
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
            throw new ApiException(HttpStatus.NOT_FOUND, "gruppa ne naidena");
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
        List<PlantDtos.PlantResponse> responses = new ArrayList<>();
        for (PlantEntity plant : plants) {
            responses.add(toPlantResponse(plant, user));
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
        return toPlantResponse(plant, user);
    }

    @GetMapping("/api/plants/{plant_id}")
    public PlantDtos.PlantResponse getPlant(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        return toPlantResponse(plant, user);
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

        return toPlantResponse(plant, user);
    }

    @DeleteMapping("/api/plants/{plant_id}")
    @Transactional
    public CommonDtos.MessageResponse deletePlant(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        plantRepository.delete(plant);
        return new CommonDtos.MessageResponse("plant deleted");
    }

    @PostMapping("/api/plants/{plant_id}/harvest")
    @Transactional
    public CommonDtos.MessageResponse harvestPlant(
            @PathVariable("plant_id") Integer plantId,
            @Valid @RequestBody PlantDtos.PlantHarvestRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        plant.setHarvestedAt(request.harvestedAt());
        plantRepository.save(plant);
        journalService.createEntry(
                plant,
                user,
                "harvest",
                request.text(),
                request.harvestedAt(),
                null
        );
        return new CommonDtos.MessageResponse("plant harvested");
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
            throw new ApiException(HttpStatus.NOT_FOUND, "rastenie ne naideno");
        }
        return plant;
    }

    private PlantGroupEntity resolvePlantGroup(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return plantGroupRepository.getReferenceById(groupId);
    }

    private PlantDtos.PlantGroupResponse toGroupResponse(PlantGroupEntity group) {
        UserEntity owner = group.getUser();
        return new PlantDtos.PlantGroupResponse(
                group.getId(),
                group.getName(),
                owner != null ? owner.getId() : null
        );
    }

    private PlantDtos.PlantResponse toPlantResponse(PlantEntity plant, UserEntity user) {
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

        List<DeviceDtos.SensorResponse> sensors = mapSensors(sensorQueryService.listByPlant(plant));
        List<DeviceDtos.PumpResponse> pumps = mapPumps(pumpQueryService.listByPlant(plant));

        UserEntity owner = plant.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new PlantDtos.PlantResponse(
                plant.getId(),
                plant.getName(),
                plant.getPlantedAt(),
                plant.getHarvestedAt(),
                plant.getPlantType(),
                plant.getStrain(),
                plant.getGrowthStage(),
                ownerId,
                groupResponse,
                sensors,
                pumps
        );
    }

    private List<DeviceDtos.SensorResponse> mapSensors(List<SensorView> views) {
        List<DeviceDtos.SensorResponse> result = new ArrayList<>();
        for (SensorView view : views) {
            List<DeviceDtos.BoundPlantResponse> plants = new ArrayList<>();
            if (view.boundPlants() != null) {
                for (var plant : view.boundPlants()) {
                    plants.add(new DeviceDtos.BoundPlantResponse(
                            plant.id(),
                            plant.name(),
                            plant.plantedAt(),
                            plant.growthStage(),
                            plant.ageDays()
                    ));
                }
            }
            result.add(new DeviceDtos.SensorResponse(
                    view.id(),
                    view.type() != null ? view.type().name() : null,
                    view.channel(),
                    view.label(),
                    view.detected(),
                    view.lastValue(),
                    view.lastTs(),
                    plants
            ));
        }
        return result;
    }

    private List<DeviceDtos.PumpResponse> mapPumps(List<PumpView> views) {
        List<DeviceDtos.PumpResponse> result = new ArrayList<>();
        for (PumpView view : views) {
            List<DeviceDtos.PumpBoundPlantResponse> plants = new ArrayList<>();
            if (view.boundPlants() != null) {
                for (var plant : view.boundPlants()) {
                    plants.add(new DeviceDtos.PumpBoundPlantResponse(
                            plant.id(),
                            plant.name(),
                            plant.plantedAt(),
                            plant.ageDays(),
                            plant.rateMlPerHour()
                    ));
                }
            }
            result.add(new DeviceDtos.PumpResponse(
                    view.id(),
                    view.channel(),
                    view.label(),
                    view.isRunning(),
                    plants
            ));
        }
        return result;
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
