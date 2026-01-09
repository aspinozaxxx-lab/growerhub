package ru.growerhub.backend.journal;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.dto.PlantDtos;
import ru.growerhub.backend.common.AuthenticatedUser;

@RestController
@Validated
public class JournalController {
    private final JournalFacade journalFacade;

    public JournalController(JournalFacade journalFacade) {
        this.journalFacade = journalFacade;
    }

    @GetMapping("/api/plants/{plant_id}/journal")
    public List<PlantDtos.PlantJournalEntryResponse> listJournalEntries(
            @PathVariable("plant_id") Integer plantId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return journalFacade.listEntries(plantId, user).stream()
                .map(this::toJournalEntryResponse)
                .toList();
    }

    @GetMapping("/api/plants/{plant_id}/journal/export")
    public ResponseEntity<String> exportJournal(
            @PathVariable("plant_id") Integer plantId,
            @RequestParam(value = "format", defaultValue = "md") String format,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        String markdown = journalFacade.exportJournal(plantId, format, user);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plant_journal_" + plantId + ".md\"");
        headers.set(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=utf-8");
        return new ResponseEntity<>(markdown, headers, HttpStatus.OK);
    }

    @PostMapping("/api/plants/{plant_id}/journal")
    public PlantDtos.PlantJournalEntryResponse createJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @RequestBody PlantDtos.PlantJournalEntryCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        JournalEntry entry = journalFacade.createEntry(
                plantId,
                user,
                request.type(),
                request.text(),
                request.eventAt(),
                request.photoUrls()
        );
        return toJournalEntryResponse(entry);
    }

    @PatchMapping("/api/plants/{plant_id}/journal/{entry_id}")
    public PlantDtos.PlantJournalEntryResponse updateJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @RequestBody PlantDtos.PlantJournalEntryUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        JournalEntry entry = journalFacade.updateEntry(
                plantId,
                entryId,
                user,
                request.type(),
                request.text()
        );
        return toJournalEntryResponse(entry);
    }

    @DeleteMapping("/api/plants/{plant_id}/journal/{entry_id}")
    public ResponseEntity<Void> deleteJournalEntry(
            @PathVariable("plant_id") Integer plantId,
            @PathVariable("entry_id") Integer entryId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        journalFacade.deleteEntry(plantId, entryId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/journal/photos/{photo_id}")
    public ResponseEntity<byte[]> getJournalPhoto(
            @PathVariable("photo_id") Integer photoId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        JournalPhotoData photo = journalFacade.getPhoto(photoId, user);
        if (photo == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "foto ne naideno ili nedostupno");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, photo.contentType());
        return new ResponseEntity<>(photo.data(), headers, HttpStatus.OK);
    }

    private PlantDtos.PlantJournalEntryResponse toJournalEntryResponse(JournalEntry entry) {
        List<PlantDtos.PlantJournalPhotoResponse> photos = entry.photos() != null
                ? entry.photos().stream()
                .map(photo -> new PlantDtos.PlantJournalPhotoResponse(
                        photo.id(),
                        photo.url(),
                        photo.caption(),
                        photo.hasData()
                ))
                .toList()
                : List.of();

        PlantDtos.PlantJournalWateringDetailsResponse details = null;
        if (entry.wateringDetails() != null) {
            JournalWateringDetails info = entry.wateringDetails();
            details = new PlantDtos.PlantJournalWateringDetailsResponse(
                    info.waterVolumeL(),
                    info.durationS(),
                    info.ph(),
                    info.fertilizersPerLiter()
            );
        }

        return new PlantDtos.PlantJournalEntryResponse(
                entry.id(),
                entry.plantId(),
                entry.userId(),
                entry.type(),
                entry.text(),
                entry.eventAt(),
                entry.createdAt(),
                photos,
                details
        );
    }
}
