package ru.growerhub.backend.journal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.journal.contract.JournalEntry;
import ru.growerhub.backend.journal.engine.JournalService;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.journal.contract.JournalPhoto;
import ru.growerhub.backend.journal.contract.JournalPhotoData;
import ru.growerhub.backend.journal.contract.JournalWateringDetails;
import ru.growerhub.backend.journal.contract.JournalWateringInfo;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.PlantInfo;

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
    private final PlantFacade plantFacade;

    public JournalFacade(
            PlantJournalEntryRepository entryRepository,
            PlantJournalPhotoRepository photoRepository,
            PlantJournalWateringDetailsRepository wateringDetailsRepository,
            JournalService journalService,
            @Lazy PlantFacade plantFacade
    ) {
        this.entryRepository = entryRepository;
        this.photoRepository = photoRepository;
        this.wateringDetailsRepository = wateringDetailsRepository;
        this.journalService = journalService;
        this.plantFacade = plantFacade;
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> listEntries(Integer plantId, AuthenticatedUser user) {
        plantFacade.requireOwnedPlantInfo(plantId, user);
        List<PlantJournalEntryEntity> entries =
                entryRepository.findAllByPlantIdOrderByEventAtDesc(plantId);
        List<JournalEntry> responses = new ArrayList<>();
        for (PlantJournalEntryEntity entry : entries) {
            responses.add(toJournalEntry(entry));
        }
        return responses;
    }

    /**
     * Vozvrashchaet poslednii poliv dlya rastenija.
     */
    @Transactional(readOnly = true)
    public JournalWateringInfo getLastWatering(Integer plantId, AuthenticatedUser user) {
        plantFacade.requireOwnedPlantInfo(plantId, user);
        PlantJournalEntryEntity entry = entryRepository
                .findTopByPlantIdAndTypeOrderByEventAtDesc(plantId, "watering")
                .orElse(null);
        if (entry == null) {
            return null;
        }
        PlantJournalWateringDetailsEntity details = wateringDetailsRepository
                .findByJournalEntry_Id(entry.getId())
                .orElse(null);
        if (details == null) {
            return null;
        }
        return new JournalWateringInfo(
                details.getWaterVolumeL(),
                details.getDurationS(),
                details.getPh(),
                details.getFertilizersPerLiter(),
                entry.getEventAt()
        );
    }

    @Transactional(readOnly = true)
    public String exportJournal(Integer plantId, String format, AuthenticatedUser user) {
        if (!"md".equals(format)) {
            throw new DomainException("bad_request", "podderzhivaetsya tolko format=md");
        }
        PlantInfo plant = plantFacade.requireOwnedPlantInfo(plantId, user);
        List<PlantJournalEntryEntity> entries =
                entryRepository.findAllByPlantIdOrderByEventAtAsc(plantId);
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime resolvedEventAt = eventAt != null ? eventAt : now;
        plantFacade.requireOwnedPlantInfo(plantId, user);
        PlantJournalEntryEntity entry = journalService.createEntry(
                plantId,
                user.id(),
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
        plantFacade.requireOwnedPlantInfo(plantId, user);
        PlantJournalEntryEntity entry = entryRepository
                .findByIdAndPlantIdAndUserId(entryId, plantId, user.id())
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
        plantFacade.requireOwnedPlantInfo(plantId, user);
        PlantJournalEntryEntity entry = entryRepository
                .findByIdAndPlantIdAndUserId(entryId, plantId, user.id())
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
        if (entry == null) {
            throw new DomainException("not_found", "foto ne naideno ili nedostupno");
        }
        try {
            plantFacade.requireOwnedPlantInfo(entry.getPlantId(), user);
        } catch (DomainException ex) {
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
        List<JournalService.WateringTarget> mapped = new ArrayList<>();
        for (WateringTarget target : targets) {
            try {
                plantFacade.requireOwnedPlantInfo(target.plantId(), user);
            } catch (DomainException ex) {
                continue;
            }
            mapped.add(new JournalService.WateringTarget(target.plantId(), target.durationS(), target.waterVolumeL()));
        }
        journalService.createWateringEntries(mapped, user.id(), eventAt, ph, fertilizersPerLiter);
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

        return new JournalEntry(
                entry.getId(),
                entry.getPlantId(),
                entry.getUserId(),
                entry.getType(),
                entry.getText(),
                entry.getEventAt(),
                entry.getCreatedAt(),
                photoResponses,
                detailsResponse
        );
    }

    private String buildJournalMarkdown(PlantInfo plant, List<PlantJournalEntryEntity> entries) {
        LocalDate plantedDate = plant.plantedAt() != null
                ? plant.plantedAt().toLocalDate()
                : LocalDate.now();
        long ageDays = java.time.temporal.ChronoUnit.DAYS.between(plantedDate, LocalDate.now());
        List<String> lines = new ArrayList<>();
        lines.add("# Zhurnal rasteniya");
        lines.add("");
        lines.add("Nazvanie: " + plant.name());
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
