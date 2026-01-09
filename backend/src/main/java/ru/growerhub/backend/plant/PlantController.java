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
import ru.growerhub.backend.common.AuthenticatedUser;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.PumpView;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.SensorView;

@RestController
@Validated
public class PlantController {
    private final PlantFacade plantFacade;
    private final SensorFacade sensorFacade;
    private final PumpFacade pumpFacade;

    public PlantController(
            PlantFacade plantFacade,
            SensorFacade sensorFacade,
            PumpFacade pumpFacade
    ) {
        this.plantFacade = plantFacade;
        this.sensorFacade = sensorFacade;
        this.pumpFacade = pumpFacade;
    }

    @GetMapping("/api/plant-groups")
    public List<PlantDtos.PlantGroupResponse> listPlantGroups(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return plantFacade.listGroups(user).stream()
                .map(group -> new PlantDtos.PlantGroupResponse(group.id(), group.name(), group.userId()))
                .toList();
    }

    @PostMapping("/api/plant-groups")
    public PlantDtos.PlantGroupResponse createPlantGroup(
            @Valid @RequestBody PlantDtos.PlantGroupCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PlantGroupInfo group = plantFacade.createGroup(request.name(), user);
        return new PlantDtos.PlantGroupResponse(group.id(), group.name(), group.userId());
    }

    @PatchMapping("/api/plant-groups/{group_id}")
    public PlantDtos.PlantGroupResponse updatePlantGroup(
            @PathVariable("group_id") Integer groupId,
            @Valid @RequestBody PlantDtos.PlantGroupUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PlantGroupInfo group = plantFacade.updateGroup(groupId, request.name(), user);
        return new PlantDtos.PlantGroupResponse(group.id(), group.name(), group.userId());
    }

    @DeleteMapping("/api/plant-groups/{group_id}")
    public CommonDtos.MessageResponse deletePlantGroup(
            @PathVariable("group_id") Integer groupId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        plantFacade.deleteGroup(groupId, user);
        return new CommonDtos.MessageResponse("group deleted");
    }

    @GetMapping("/api/plants")
    public List<PlantDtos.PlantResponse> listPlants(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<PlantInfo> plants = plantFacade.listPlants(user);
        List<PlantDtos.PlantResponse> responses = new ArrayList<>();
        for (PlantInfo plant : plants) {
            responses.add(toPlantResponse(plant));
        }
        return responses;
    }

    @PostMapping("/api/plants")
    public PlantDtos.PlantResponse createPlant(
            @Valid @RequestBody PlantDtos.PlantCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PlantInfo plant = plantFacade.createPlant(
                new PlantFacade.PlantCreateCommand(
                        request.name(),
                        request.plantedAt(),
                        request.plantGroupId(),
                        request.plantType(),
                        request.strain(),
                        request.growthStage()
                ),
                user
        );
        return toPlantResponse(plant);
    }

    @GetMapping("/api/plants/{plant_id}")
    public PlantDtos.PlantResponse getPlant(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PlantInfo plant = plantFacade.getPlant(plantId, user);
        return toPlantResponse(plant);
    }

    @PatchMapping("/api/plants/{plant_id}")
    public PlantDtos.PlantResponse updatePlant(
            @PathVariable("plant_id") Integer plantId,
            @RequestBody JsonNode request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PlantFacade.PlantUpdateCommand command = parseUpdateCommand(request);
        PlantInfo plant = plantFacade.updatePlant(plantId, command, user);
        return toPlantResponse(plant);
    }

    @DeleteMapping("/api/plants/{plant_id}")
    public CommonDtos.MessageResponse deletePlant(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        plantFacade.deletePlant(plantId, user);
        return new CommonDtos.MessageResponse("plant deleted");
    }

    @PostMapping("/api/plants/{plant_id}/harvest")
    public CommonDtos.MessageResponse harvestPlant(
            @PathVariable("plant_id") Integer plantId,
            @Valid @RequestBody PlantDtos.PlantHarvestRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        plantFacade.harvestPlant(
                plantId,
                new PlantFacade.PlantHarvestCommand(request.harvestedAt(), request.text()),
                user
        );
        return new CommonDtos.MessageResponse("plant harvested");
    }

    @GetMapping("/api/admin/plants")
    public List<PlantDtos.AdminPlantResponse> listAdminPlants(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<AdminPlantInfo> plants = plantFacade.listAdminPlants(user);
        List<PlantDtos.AdminPlantResponse> responses = new ArrayList<>();
        for (AdminPlantInfo plant : plants) {
            responses.add(new PlantDtos.AdminPlantResponse(
                    plant.id(),
                    plant.name(),
                    plant.ownerEmail(),
                    plant.ownerUsername(),
                    plant.ownerId(),
                    plant.groupName()
            ));
        }
        return responses;
    }

    private PlantDtos.PlantResponse toPlantResponse(PlantInfo plant) {
        PlantGroupInfo group = plant.plantGroup();
        PlantDtos.PlantGroupResponse groupResponse = group != null
                ? new PlantDtos.PlantGroupResponse(group.id(), group.name(), group.userId())
                : null;
        List<DeviceDtos.SensorResponse> sensors = mapSensors(sensorFacade.listByPlantId(plant.id()));
        List<DeviceDtos.PumpResponse> pumps = mapPumps(pumpFacade.listByPlantId(plant.id()));
        return new PlantDtos.PlantResponse(
                plant.id(),
                plant.name(),
                plant.plantedAt(),
                plant.harvestedAt(),
                plant.plantType(),
                plant.strain(),
                plant.growthStage(),
                plant.userId(),
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

    private PlantFacade.PlantUpdateCommand parseUpdateCommand(JsonNode request) {
        String name = null;
        LocalDateTime plantedAt = null;
        Integer groupId = null;
        boolean groupProvided = false;
        String plantType = null;
        String strain = null;
        String growthStage = null;

        if (request.has("name")) {
            name = textValue(request.get("name"));
        }
        if (request.has("planted_at")) {
            plantedAt = parseDateValue(request.get("planted_at"), "planted_at");
        }
        if (request.has("plant_group_id")) {
            groupProvided = true;
            groupId = intValue(request.get("plant_group_id"), "plant_group_id");
        }
        if (request.has("plant_type")) {
            plantType = textValue(request.get("plant_type"));
        }
        if (request.has("strain")) {
            strain = textValue(request.get("strain"));
        }
        if (request.has("growth_stage")) {
            growthStage = textValue(request.get("growth_stage"));
        }

        return new PlantFacade.PlantUpdateCommand(
                name,
                plantedAt,
                groupId,
                groupProvided,
                plantType,
                strain,
                growthStage
        );
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

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}
