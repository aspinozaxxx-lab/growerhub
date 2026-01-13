package ru.growerhub.backend.journal;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.journal.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.PlantJournalPhotoEntity;
import ru.growerhub.backend.journal.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.internal.JournalService;
import ru.growerhub.backend.journal.internal.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.internal.PlantJournalPhotoRepository;
import ru.growerhub.backend.journal.internal.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.journal.contract.JournalEntry;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.user.UserEntity;

@Service
public class JournalFacade {
    private static final Set<String> JOURNAL_TYPES = Set.of(
            "watering",
            "feeding",
            "harvest",
            "note",
            "photo",
            "other"
    );

    private final PlantJournalEntryRepository entryRepository;
    private final PlantJournalPhotoRepository photoRepository;
    private final PlantJournalWateringDetailsRepository wateringDetailsRepository;
    private final JournalService journalService;
    private final EntityManager entityManager;

    public JournalFacade(
            PlantJournalEntryRepository entryRepository,
            PlantJournalPhotoRepository photoRepository,
            PlantJournalWateringDetailsRepository wateringDetailsRepository,
            JournalService journalService,
            EntityManager entityManager
    ) {
        this.entryRepository = entryRepository;
        this.photoRepository = photoRepository;
        this.wateringDetailsRepository = wateringDetailsRepository;
        this.journalService = journalService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> listEntries(Integer plantId, AuthenticatedUser user) {
        requireUserPlant(plantId, user);
        List<PlantJournalEntryEntity> entries =
                entryRepository.findAllByPlant_IdOrderByEventAtDesc(plantId);
        List<JournalEntry> responses = new ArrayList<>();
        for (PlantJournalEntryEntity entry : entries) {
            responses.add(toJournalEntry(entry));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public String exportJournal(Integer plantId, String format, AuthenticatedUser user) {
        if (!"md".equals(format)) {
            throw new DomainException("bad_request", "podderzhivaetsya tolko format=md");
        }
        PlantEntity plant = requireUserPlant(plantId, user);
        List<PlantJournalEntryEntity> entries =
                entryRepository.findAllByPlant_IdOrderByEventAtAsc(plantId);
        return buildJournalMarkdown(plant, entries);
    }

    @Transactional
    public JournalEntry createEntry(
            Integer plantId,
            AuthenticatedUser user,
            String type,
            String text,
            LocalDateTime eventAt,
            List<String> photoUrls
    ) {
        validateJournalType(type);
        PlantEntity plant = requireUserPlant(plantId, user);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime resolvedEventAt = eventAt != null ? eventAt : now;
        UserEntity userRef = entityManager.getReference(UserEntity.class, user.id());
        PlantJournalEntryEntity entry = journalService.createEntry(
                plant,
                userRef,
                type,
                text,
                resolvedEventAt,
                photoUrls
        );
        return toJournalEntry(entry);
    }

    @Transactional
    public JournalEntry updateEntry(
            Integer plantId,
            Integer entryId,
            AuthenticatedUser user,
            String type,
            String text
    ) {
        requireUserPlant(plantId, user);
        PlantJournalEntryEntity entry = entryRepository
                .findByIdAndPlant_IdAndUser_Id(entryId, plantId, user.id())
                .orElse(null);
        if (entry == null) {
            throw new DomainException("not_found", "zapis' ne naidena");
        }

        if (type != null) {
            validateJournalType(type);
        }
        PlantJournalEntryEntity updated = journalService.updateEntry(entry, type, text);
        return toJournalEntry(updated);
    }

    @Transactional
    public void deleteEntry(Integer plantId, Integer entryId, AuthenticatedUser user) {
        requireUserPlant(plantId, user);
        PlantJournalEntryEntity entry = entryRepository
                .findByIdAndPlant_IdAndUser_Id(entryId, plantId, user.id())
                .orElse(null);
        if (entry == null) {
            throw new DomainException("not_found", "zapis' ne naidena");
        }
        entryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public JournalPhotoData getPhoto(Integer photoId, AuthenticatedUser user) {
        PlantJournalPhotoEntity photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            throw new DomainException("not_found", "foto ne naideno ili nedostupno");
        }
        PlantJournalEntryEntity entry = photo.getJournalEntry();
        PlantEntity plant = entry != null ? entry.getPlant() : null;
        UserEntity owner = plant != null ? plant.getUser() : null;
        if (owner == null || user == null || !owner.getId().equals(user.id())) {
            throw new DomainException("not_found", "foto ne naideno ili nedostupno");
        }
        byte[] data = photo.getData();
        if (data == null || data.length == 0) {
            throw new DomainException("not_found", "binarnye dannye dlya etogo foto otsutstvuyut");
        }
        String contentType = photo.getContentType() != null ? photo.getContentType() : "application/octet-stream";
        return new JournalPhotoData(data, contentType);
    }

    @Transactional
    public void createWateringEntries(
            List<WateringTarget> targets,
            AuthenticatedUser user,
            LocalDateTime eventAt,
            Double ph,
            String fertilizersPerLiter
    ) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        UserEntity userRef = entityManager.getReference(UserEntity.class, user.id());
        List<JournalService.WateringTarget> mapped = new ArrayList<>();
        for (WateringTarget target : targets) {
            PlantEntity plant = entityManager.find(PlantEntity.class, target.plantId());
            if (plant == null) {
                continue;
            }
            mapped.add(new JournalService.WateringTarget(plant, target.durationS(), target.waterVolumeL()));
        }
        journalService.createWateringEntries(mapped, userRef, eventAt, ph, fertilizersPerLiter);
    }

    private PlantEntity requireUserPlant(Integer plantId, AuthenticatedUser user) {
        if (plantId == null || user == null) {
            throw new DomainException("not_found", "rastenie ne naideno");
        }
        PlantEntity plant = entityManager.find(PlantEntity.class, plantId);
        if (plant == null || plant.getUser() == null || !plant.getUser().getId().equals(user.id())) {
            throw new DomainException("not_found", "rastenie ne naideno");
        }
        return plant;
    }

    private JournalEntry toJournalEntry(PlantJournalEntryEntity entry) {
        List<PlantJournalPhotoEntity> photos =
                photoRepository.findAllByJournalEntry_Id(entry.getId());
        List<JournalPhoto> photoResponses = new ArrayList<>();
        for (PlantJournalPhotoEntity photo : photos) {
            boolean hasData = photo.getData() != null && photo.getData().length > 0;
            photoResponses.add(new JournalPhoto(
                    photo.getId(),
                    photo.getUrl(),
                    photo.getCaption(),
                    hasData
            ));
        }

        JournalWateringDetails detailsResponse = null;
        if ("watering".equals(entry.getType())) {
            PlantJournalWateringDetailsEntity details = wateringDetailsRepository
                    .findByJournalEntry_Id(entry.getId())
                    .orElse(null);
            if (details != null) {
                detailsResponse = new JournalWateringDetails(
                        details.getWaterVolumeL(),
                        details.getDurationS(),
                        details.getPh(),
                        details.getFertilizersPerLiter()
                );
            }
        }

        UserEntity owner = entry.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new JournalEntry(
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
        lines.add("# Zhurnal rasteniya");
        lines.add("");
        lines.add("Nazvanie: " + plant.getName());
        lines.add("Data posadki: " + plantedDate);
        lines.add("Tekushchii vozrast: " + Math.max(ageDays, 0) + " dnei");
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
            String label = "Nablyudenie";
            String text = entry.getText() != null ? entry.getText() : "";

            if ("watering".equals(entry.getType())) {
                icon = "??";
                label = "Poliv";
                PlantJournalWateringDetailsEntity details = wateringDetailsRepository
                        .findByJournalEntry_Id(entry.getId())
                        .orElse(null);
                String detailsText = buildWateringText(details);
                if (!detailsText.isEmpty()) {
                    text = detailsText;
                }
            } else if ("feeding".equals(entry.getType())) {
                icon = "??";
                label = "Uhod";
            } else if ("photo".equals(entry.getType())) {
                icon = "??";
                label = "Foto";
                if (text.isEmpty()) {
                    text = "Foto";
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
        return formatted.replace(".", ",") + " l";
    }

    private void validateJournalType(String type) {
        if (type == null) {
            throw new DomainException("unprocessable", "Field required");
        }
        if (!JOURNAL_TYPES.contains(type)) {
            throw new DomainException("unprocessable", "Input should be 'watering' or 'feeding' or 'harvest' or 'note' or 'photo' or 'other'");
        }
    }

    public record WateringTarget(Integer plantId, int durationS, double waterVolumeL) {
    }
}

