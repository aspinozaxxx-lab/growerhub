package ru.growerhub.backend.journal;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.ApiValidationError;
import ru.growerhub.backend.api.ApiValidationErrorItem;
import ru.growerhub.backend.api.ApiValidationException;
import ru.growerhub.backend.api.dto.PlantDtos;
import ru.growerhub.backend.plant.PlantEntity;
import ru.growerhub.backend.plant.PlantRepository;
import ru.growerhub.backend.user.UserEntity;

@RestController
@Validated
public class JournalController {
    private static final Set<String> JOURNAL_TYPES = Set.of(
            "watering",
            "feeding",
            "note",
            "photo",
            "other"
    );

    private final PlantRepository plantRepository;
    private final PlantJournalEntryRepository plantJournalEntryRepository;
    private final PlantJournalPhotoRepository plantJournalPhotoRepository;
    private final PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository;
    private final JournalService journalService;

    public JournalController(
            PlantRepository plantRepository,
            PlantJournalEntryRepository plantJournalEntryRepository,
            PlantJournalPhotoRepository plantJournalPhotoRepository,
            PlantJournalWateringDetailsRepository plantJournalWateringDetailsRepository,
            JournalService journalService
    ) {
        this.plantRepository = plantRepository;
        this.plantJournalEntryRepository = plantJournalEntryRepository;
        this.plantJournalPhotoRepository = plantJournalPhotoRepository;
        this.plantJournalWateringDetailsRepository = plantJournalWateringDetailsRepository;
        this.journalService = journalService;
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
            @RequestBody PlantDtos.PlantJournalEntryCreateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        validateJournalType(request.type());
        PlantEntity plant = requireUserPlant(plantId, user);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime eventAt = request.eventAt() != null ? request.eventAt() : now;
        PlantJournalEntryEntity entry = journalService.createEntry(
                plant,
                user,
                request.type(),
                request.text(),
                eventAt,
                request.photoUrls()
        );
        return toJournalEntryResponse(entry);
    }

    @PatchMapping("/api/plants/{plant_id}/journal/{entry_id}")
    @Transactional
    public PlantDtos.PlantJournalEntryResponse updateJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @RequestBody PlantDtos.PlantJournalEntryUpdateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireUserPlant(plantId, user);
        PlantJournalEntryEntity entry = plantJournalEntryRepository
                .findByIdAndPlant_IdAndUser_Id(entryId, plantId, user.getId())
                .orElse(null);
        if (entry == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "zapis' ne naidena");
        }

        String type = request.type();
        if (type != null) {
            validateJournalType(type);
        }
        PlantJournalEntryEntity updated = journalService.updateEntry(entry, type, request.text());
        return toJournalEntryResponse(updated);
    }

    @DeleteMapping("/api/plants/{plant_id}/journal/{entry_id}")
    @Transactional
    public ResponseEntity<Void> deleteJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireUserPlant(plantId, user);
        PlantJournalEntryEntity entry = plantJournalEntryRepository
                .findByIdAndPlant_IdAndUser_Id(entryId, plantId, user.getId())
                .orElse(null);
        if (entry == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "zapis' ne naidena");
        }
        plantJournalEntryRepository.delete(entry);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/journal/photos/{photo_id}")
    public ResponseEntity<byte[]> getJournalPhoto(
            @PathVariable("photo_id") Integer photoId,
            @AuthenticationPrincipal UserEntity user
    ) {
        PlantJournalPhotoEntity photo = plantJournalPhotoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "foto ne naideno ili nedostupno");
        }
        PlantJournalEntryEntity entry = photo.getJournalEntry();
        PlantEntity plant = entry != null ? entry.getPlant() : null;
        UserEntity owner = plant != null ? plant.getUser() : null;
        if (owner == null || !owner.getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "foto ne naideno ili nedostupno");
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

    private PlantEntity requireUserPlant(Integer plantId, UserEntity user) {
        PlantEntity plant = plantRepository.findByIdAndUser_Id(plantId, user.getId()).orElse(null);
        if (plant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "rastenie ne naideno");
        }
        return plant;
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
                PlantJournalWateringDetailsEntity details = plantJournalWateringDetailsRepository
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
            ApiValidationErrorItem item = new ApiValidationErrorItem(
                    List.of("body", "type"),
                    "Field required",
                    "value_error.missing"
            );
            throw new ApiValidationException(new ApiValidationError(List.of(item)));
        }
        if (!JOURNAL_TYPES.contains(type)) {
            ApiValidationErrorItem item = new ApiValidationErrorItem(
                    List.of("body", "type"),
                    "Input should be 'watering' or 'feeding' or 'note' or 'photo' or 'other'",
                    "literal_error"
            );
            throw new ApiValidationException(new ApiValidationError(List.of(item)));
        }
    }
}
