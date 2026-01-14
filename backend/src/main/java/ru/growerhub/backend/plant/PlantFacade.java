package ru.growerhub.backend.plant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.plant.PlantHistorySettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.plant.contract.AdminPlantInfo;
import ru.growerhub.backend.plant.contract.PlantGroupInfo;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.plant.contract.PlantMetricPoint;
import ru.growerhub.backend.plant.contract.PlantMetricBucketPoint;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.engine.PlantHistoryService;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantGroupEntity;
import ru.growerhub.backend.plant.jpa.PlantGroupRepository;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.sensor.contract.SensorReadingSummary;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.user.UserFacade;

@Service
public class PlantFacade {
    private final PlantRepository plantRepository;
    private final PlantGroupRepository plantGroupRepository;
    private final PlantMetricSampleRepository plantMetricSampleRepository;
    private final PlantHistoryService plantHistoryService;
    private final JournalFacade journalFacade;
    private final UserFacade userFacade;
    private final PlantHistorySettings historySettings;

    public PlantFacade(
            PlantRepository plantRepository,
            PlantGroupRepository plantGroupRepository,
            PlantMetricSampleRepository plantMetricSampleRepository,
            PlantHistoryService plantHistoryService,
            JournalFacade journalFacade,
            @Lazy UserFacade userFacade,
            PlantHistorySettings historySettings
    ) {
        this.plantRepository = plantRepository;
        this.plantGroupRepository = plantGroupRepository;
        this.plantMetricSampleRepository = plantMetricSampleRepository;
        this.plantHistoryService = plantHistoryService;
        this.journalFacade = journalFacade;
        this.userFacade = userFacade;
        this.historySettings = historySettings;
    }

    @Transactional(readOnly = true)
    public List<PlantGroupInfo> listGroups(AuthenticatedUser user) {
        List<PlantGroupEntity> groups = plantGroupRepository.findAllByUserId(user.id());
        return groups.stream().map(this::toGroupInfo).toList();
    }

    @Transactional
    public PlantGroupInfo createGroup(String name, AuthenticatedUser user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PlantGroupEntity group = PlantGroupEntity.create();
        group.setName(name);
        group.setUserId(requireUserId(user));
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        plantGroupRepository.save(group);
        return toGroupInfo(group);
    }

    @Transactional
    public PlantGroupInfo updateGroup(Integer groupId, String name, AuthenticatedUser user) {
        PlantGroupEntity group = plantGroupRepository.findByIdAndUserId(groupId, user.id()).orElse(null);
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
        PlantGroupEntity group = plantGroupRepository.findByIdAndUserId(groupId, user.id()).orElse(null);
        if (group == null) {
            throw new DomainException("not_found", "gruppa ne naidena");
        }
        List<PlantEntity> plants = plantRepository.findAllByUserIdAndPlantGroup_Id(user.id(), groupId);
        for (PlantEntity plant : plants) {
            plant.setPlantGroup(null);
        }
        plantRepository.saveAll(plants);
        plantGroupRepository.delete(group);
    }

    @Transactional(readOnly = true)
    public List<PlantInfo> listPlants(AuthenticatedUser user) {
        List<PlantEntity> plants = plantRepository.findAllByUserId(user.id());
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
        plant.setUserId(requireUserId(user));
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
            Integer ownerId = plant.getUserId();
            UserFacade.UserProfile owner = ownerId != null ? userFacade.getUser(ownerId) : null;
            PlantGroupEntity group = plant.getPlantGroup();
            responses.add(new AdminPlantInfo(
                    plant.getId(),
                    plant.getName(),
                    owner != null ? owner.email() : null,
                    owner != null ? owner.username() : null,
                    owner != null ? owner.id() : null,
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
        int defaultHours = historySettings.getDefaultHours();
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusHours(hours != null ? hours : defaultHours);
        List<PlantMetricSampleEntity> rows = plantMetricSampleRepository
                .findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(plant.getId(), metricTypes, since);
        List<PlantMetricPoint> payload = new ArrayList<>();
        for (PlantMetricSampleEntity row : downsample(rows, historySettings.getMaxPoints())) {
            payload.add(new PlantMetricPoint(
                    row.getMetricType() != null ? row.getMetricType().name() : null,
                    row.getTs(),
                    row.getValueNumeric()
            ));
        }
        return payload;
    }

    /**
     * Vozvrashchaet bucket-istoriyu po metrikam s shagnom po vremeni (znachenie = poslednee v bucket).
     */
    @Transactional(readOnly = true)
    public List<PlantMetricBucketPoint> getBucketedHistory(
            Integer plantId,
            AuthenticatedUser user,
            List<PlantMetricType> metricTypes,
            LocalDateTime since,
            Duration bucketDuration
    ) {
        if (metricTypes == null || metricTypes.isEmpty() || since == null || bucketDuration == null) {
            return List.of();
        }
        PlantEntity plant = requireUserPlant(plantId, user);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long bucketSeconds = Math.max(1, bucketDuration.getSeconds());
        long totalSeconds = Math.max(0, Duration.between(since, now).getSeconds());
        int bucketCount = (int) Math.max(1, totalSeconds / bucketSeconds);

        List<PlantMetricSampleEntity> rows = plantMetricSampleRepository
                .findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(plant.getId(), metricTypes, since);
        Map<PlantMetricType, List<PlantMetricSampleEntity>> byMetric = new java.util.HashMap<>();
        for (PlantMetricSampleEntity row : rows) {
            if (row.getMetricType() == null) {
                continue;
            }
            byMetric.computeIfAbsent(row.getMetricType(), key -> new ArrayList<>()).add(row);
        }

        List<PlantMetricBucketPoint> payload = new ArrayList<>();
        List<PlantMetricType> distinctMetrics = metricTypes.stream().distinct().toList();
        for (PlantMetricType metricType : distinctMetrics) {
            List<PlantMetricSampleEntity> samples = byMetric.getOrDefault(metricType, List.of());
            int index = 0;
            for (int i = 0; i < bucketCount; i++) {
                LocalDateTime bucketStart = since.plusSeconds(bucketSeconds * i);
                LocalDateTime bucketEnd = bucketStart.plusSeconds(bucketSeconds);
                Double value = null;
                while (index < samples.size()) {
                    PlantMetricSampleEntity sample = samples.get(index);
                    LocalDateTime ts = sample.getTs();
                    if (ts == null || !ts.isBefore(bucketEnd)) {
                        break;
                    }
                    if (!ts.isBefore(bucketStart)) {
                        value = sample.getValueNumeric();
                    }
                    index++;
                }
                payload.add(new PlantMetricBucketPoint(metricType.name(), bucketStart, value));
            }
        }
        return payload;
    }

    public PlantInfo requireOwnedPlantInfo(Integer plantId, AuthenticatedUser user) {
        PlantEntity plant = requireUserPlant(plantId, user);
        return toPlantInfo(plant);
    }

    @Transactional(readOnly = true)
    public PlantInfo getPlantInfoById(Integer plantId) {
        if (plantId == null) {
            return null;
        }
        PlantEntity plant = plantRepository.findById(plantId).orElse(null);
        return plant != null ? toPlantInfo(plant) : null;
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
        PlantEntity plant = plantRepository.findByIdAndUserId(plantId, user.id()).orElse(null);
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
        Integer ownerId = group.getUserId();
        return new PlantGroupInfo(
                group.getId(),
                group.getName(),
                ownerId
        );
    }

    private PlantInfo toPlantInfo(PlantEntity plant) {
        PlantGroupEntity group = plant.getPlantGroup();
        PlantGroupInfo groupInfo = null;
        if (group != null) {
            groupInfo = new PlantGroupInfo(
                    group.getId(),
                    group.getName(),
                    group.getUserId()
            );
        }
        Integer ownerId = plant.getUserId();
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
        if (points.size() <= maxPoints) {
            return points;
        }
        int step = (int) Math.ceil(points.size() / (double) maxPoints);
        List<PlantMetricSampleEntity> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        return sampled;
    }

    private Integer requireUserId(AuthenticatedUser user) {
        if (user == null) {
            throw new DomainException("forbidden", "polzovatel' ne naiden");
        }
        return user.id();
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





