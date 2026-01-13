package ru.growerhub.backend.plant.facade;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.plant.contract.AdminPlantInfo;
import ru.growerhub.backend.plant.contract.PlantGroupInfo;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.plant.contract.PlantMetricPoint;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.engine.PlantHistoryService;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantGroupEntity;
import ru.growerhub.backend.plant.jpa.PlantGroupRepository;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.sensor.SensorReadingSummary;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.user.UserEntity;

@Service
public class PlantFacade {
    private static final int MAX_HISTORY_POINTS = 200;

    private final PlantRepository plantRepository;
    private final PlantGroupRepository plantGroupRepository;
    private final PlantMetricSampleRepository plantMetricSampleRepository;
    private final PlantHistoryService plantHistoryService;
    private final JournalFacade journalFacade;
    private final EntityManager entityManager;

    public PlantFacade(
            PlantRepository plantRepository,
            PlantGroupRepository plantGroupRepository,
            PlantMetricSampleRepository plantMetricSampleRepository,
            PlantHistoryService plantHistoryService,
            JournalFacade journalFacade,
            EntityManager entityManager
    ) {
        this.plantRepository = plantRepository;
        this.plantGroupRepository = plantGroupRepository;
        this.plantMetricSampleRepository = plantMetricSampleRepository;
        this.plantHistoryService = plantHistoryService;
        this.journalFacade = journalFacade;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<PlantGroupInfo> listGroups(AuthenticatedUser user) {
        List<PlantGroupEntity> groups = plantGroupRepository.findAllByUser_Id(user.id());
        return groups.stream().map(this::toGroupInfo).toList();
    }

    @Transactional
    public PlantGroupInfo createGroup(String name, AuthenticatedUser user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PlantGroupEntity group = PlantGroupEntity.create();
        group.setName(name);
        group.setUser(requireUser(user));
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        plantGroupRepository.save(group);
        return toGroupInfo(group);
    }

    @Transactional
    public PlantGroupInfo updateGroup(Integer groupId, String name, AuthenticatedUser user) {
        PlantGroupEntity group = plantGroupRepository.findByIdAndUser_Id(groupId, user.id()).orElse(null);
        if (group == null) {
            throw new DomainException("not_found", "gruppa ne naidena");
        }
        group.setName(name);
        group.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        plantGroupRepository.save(group);
        return toGroupInfo(group);
    }

    @Transactional
    public void deleteGroup(Integer groupId, AuthenticatedUser user) {
        PlantGroupEntity group = plantGroupRepository.findByIdAndUser_Id(groupId, user.id()).orElse(null);
        if (group == null) {
            throw new DomainException("not_found", "gruppa ne naidena");
        }
        List<PlantEntity> plants = plantRepository.findAllByUser_IdAndPlantGroup_Id(user.id(), groupId);
        for (PlantEntity plant : plants) {
            plant.setPlantGroup(null);
        }
        plantRepository.saveAll(plants);
        plantGroupRepository.delete(group);
    }

    @Transactional(readOnly = true)
    public List<PlantInfo> listPlants(AuthenticatedUser user) {
        List<PlantEntity> plants = plantRepository.findAllByUser_Id(user.id());
        List<PlantInfo> responses = new ArrayList<>();
        for (PlantEntity plant : plants) {
            responses.add(toPlantInfo(plant));
        }
        return responses;
    }

    @Transactional
    public PlantInfo createPlant(PlantCreateCommand command, AuthenticatedUser user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime plantedAt = command.plantedAt() != null ? command.plantedAt() : now;
        PlantEntity plant = PlantEntity.create();
        plant.setName(command.name());
        plant.setPlantedAt(plantedAt);
        plant.setUser(requireUser(user));
        plant.setPlantGroup(resolvePlantGroup(command.plantGroupId()));
        plant.setPlantType(command.plantType());
        plant.setStrain(command.strain());
        plant.setGrowthStage(command.growthStage());
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        plantRepository.save(plant);
        return toPlantInfo(plant);
    }

    @Transactional(readOnly = true)
    public PlantInfo getPlant(Integer plantId, AuthenticatedUser user) {
        PlantEntity plant = requireUserPlant(plantId, user);
        return toPlantInfo(plant);
    }

    @Transactional
    public PlantInfo updatePlant(Integer plantId, PlantUpdateCommand command, AuthenticatedUser user) {
        PlantEntity plant = requireUserPlant(plantId, user);
        boolean changed = false;

        if (command.name() != null) {
            plant.setName(command.name());
            changed = true;
        }
        if (command.plantedAt() != null) {
            plant.setPlantedAt(command.plantedAt());
            changed = true;
        }
        if (command.plantGroupProvided()) {
            Integer groupId = command.plantGroupId();
            plant.setPlantGroup(groupId != null ? resolvePlantGroup(groupId) : null);
            changed = true;
        }
        if (command.plantType() != null) {
            plant.setPlantType(command.plantType());
            changed = true;
        }
        if (command.strain() != null) {
            plant.setStrain(command.strain());
            changed = true;
        }
        if (command.growthStage() != null) {
            plant.setGrowthStage(command.growthStage());
            changed = true;
        }

        if (changed) {
            plant.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            plantRepository.save(plant);
        }

        return toPlantInfo(plant);
    }

    @Transactional
    public void deletePlant(Integer plantId, AuthenticatedUser user) {
        PlantEntity plant = requireUserPlant(plantId, user);
        plantRepository.delete(plant);
    }

    @Transactional
    public void harvestPlant(Integer plantId, PlantHarvestCommand command, AuthenticatedUser user) {
        PlantEntity plant = requireUserPlant(plantId, user);
        plant.setHarvestedAt(command.harvestedAt());
        plantRepository.save(plant);
        journalFacade.createEntry(
                plantId,
                user,
                "harvest",
                command.text(),
                command.harvestedAt(),
                null
        );
    }

    @Transactional(readOnly = true)
    public List<AdminPlantInfo> listAdminPlants(AuthenticatedUser user) {
        requireAdmin(user);
        List<PlantEntity> plants = plantRepository.findAll();
        List<AdminPlantInfo> responses = new ArrayList<>();
        for (PlantEntity plant : plants) {
            UserEntity owner = plant.getUser();
            PlantGroupEntity group = plant.getPlantGroup();
            responses.add(new AdminPlantInfo(
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

    @Transactional(readOnly = true)
    public List<PlantMetricPoint> getHistory(
            Integer plantId,
            Integer hours,
            String metrics,
            AuthenticatedUser user
    ) {
        PlantEntity plant = requireUserPlant(plantId, user);
        List<PlantMetricType> metricTypes = parseMetricTypes(metrics);
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours != null ? hours : 24);
        List<PlantMetricSampleEntity> rows = plantMetricSampleRepository
                .findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(plant.getId(), metricTypes, since);
        List<PlantMetricPoint> payload = new ArrayList<>();
        for (PlantMetricSampleEntity row : downsample(rows, MAX_HISTORY_POINTS)) {
            payload.add(new PlantMetricPoint(
                    row.getMetricType() != null ? row.getMetricType().name() : null,
                    row.getTs(),
                    row.getValueNumeric()
            ));
        }
        return payload;
    }

    @Transactional
    public void recordFromSensorBindings(List<SensorReadingSummary> summaries) {
        plantHistoryService.recordFromSensorBindings(summaries);
    }

    @Transactional
    public void recordWateringEvent(Integer plantId, double volumeL, LocalDateTime eventAt) {
        PlantEntity plant = plantRepository.findById(plantId).orElse(null);
        if (plant == null) {
            return;
        }
        plantHistoryService.recordWateringEvent(plant, volumeL, eventAt);
    }

    private PlantEntity requireUserPlant(Integer plantId, AuthenticatedUser user) {
        PlantEntity plant = plantRepository.findByIdAndUser_Id(plantId, user.id()).orElse(null);
        if (plant == null) {
            throw new DomainException("not_found", "rastenie ne naideno");
        }
        return plant;
    }

    private PlantGroupEntity resolvePlantGroup(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return plantGroupRepository.getReferenceById(groupId);
    }

    private PlantGroupInfo toGroupInfo(PlantGroupEntity group) {
        UserEntity owner = group.getUser();
        return new PlantGroupInfo(
                group.getId(),
                group.getName(),
                owner != null ? owner.getId() : null
        );
    }

    private PlantInfo toPlantInfo(PlantEntity plant) {
        PlantGroupEntity group = plant.getPlantGroup();
        PlantGroupInfo groupInfo = null;
        if (group != null) {
            UserEntity owner = group.getUser();
            groupInfo = new PlantGroupInfo(
                    group.getId(),
                    group.getName(),
                    owner != null ? owner.getId() : null
            );
        }
        UserEntity owner = plant.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new PlantInfo(
                plant.getId(),
                plant.getName(),
                plant.getPlantedAt(),
                plant.getHarvestedAt(),
                plant.getPlantType(),
                plant.getStrain(),
                plant.getGrowthStage(),
                ownerId,
                groupInfo
        );
    }

    private List<PlantMetricType> parseMetricTypes(String metrics) {
        if (metrics == null || metrics.isBlank()) {
            return List.of(PlantMetricType.SOIL_MOISTURE);
        }
        Set<String> parts = Set.of(metrics.split(","));
        List<PlantMetricType> result = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim().toUpperCase(Locale.ROOT);
            try {
                result.add(PlantMetricType.valueOf(value));
            } catch (IllegalArgumentException ex) {
                throw new DomainException("unprocessable", "neizvestnyi metric_type: " + value);
            }
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    private List<PlantMetricSampleEntity> downsample(List<PlantMetricSampleEntity> points, int maxPoints) {
        if (points.size() <= MAX_HISTORY_POINTS) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) MAX_HISTORY_POINTS);
        List<PlantMetricSampleEntity> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }

    private UserEntity requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new DomainException("forbidden", "polzovatel' ne naiden");
        }
        return entityManager.getReference(UserEntity.class, user.id());
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new DomainException("forbidden", "Nedostatochno prav");
        }
    }

    public record PlantCreateCommand(
            String name,
            LocalDateTime plantedAt,
            Integer plantGroupId,
            String plantType,
            String strain,
            String growthStage
    ) {
    }

    public record PlantUpdateCommand(
            String name,
            LocalDateTime plantedAt,
            Integer plantGroupId,
            boolean plantGroupProvided,
            String plantType,
            String strain,
            String growthStage
    ) {
    }

    public record PlantHarvestCommand(LocalDateTime harvestedAt, String text) {
    }
}


