package ru.growerhub.backend.api;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.growerhub.backend.common.config.CareSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.util.SafeImage;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.journal.contract.CareData;
import ru.growerhub.backend.journal.contract.JournalPhoto;

@RestController
@RequestMapping("/api/care")
public class CareController {
    private final JournalFacade journal;
    private final CareSettings settings;
    public CareController(JournalFacade journal, CareSettings settings) { this.journal = journal; this.settings = settings; }

    @GetMapping("/journal")
    public CareData.Page search(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "plant_id", required = false) Integer plantId,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "") String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime until,
            @RequestParam(defaultValue = "0") int page) {
        return journal.searchCare(user, plantId, query, action, from, until, page);
    }
    @GetMapping("/summaries")
    public List<CareData.Summary> summaries(@AuthenticationPrincipal AuthenticatedUser user) { return journal.careSummaries(user); }
    @PostMapping("/plants/{plantId}/entries")
    public CareData.Item create(@PathVariable Integer plantId, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CareData.EntryCommand command) { return journal.createCareEntry(plantId, user, command); }
    @PatchMapping("/entries/{entryId}")
    public CareData.Item update(@PathVariable Integer entryId, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CareData.EntryCommand command) { return journal.updateCareEntry(entryId, user, command); }
    @DeleteMapping("/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer entryId, @AuthenticationPrincipal AuthenticatedUser user) { journal.deleteCareEntry(entryId, user); }
    @PostMapping("/entries/{entryId}/photos")
    public JournalPhoto addPhoto(@PathVariable Integer entryId, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file) throws IOException {
        byte[] jpeg = SafeImage.jpeg(file.getBytes(), settings.maxUploadBytes(), settings.maxImagePixels(), settings.imageEdge());
        return journal.addCarePhoto(entryId, user, jpeg);
    }
    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable Integer photoId, @AuthenticationPrincipal AuthenticatedUser user) { journal.deleteCarePhoto(photoId, user); }
    @GetMapping("/reminders")
    public java.util.List<CareData.Reminder> reminders(@AuthenticationPrincipal AuthenticatedUser user) { return journal.careReminders(user); }
    @PostMapping("/reminders")
    public CareData.Reminder reminder(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody CareData.ReminderCommand command) { return journal.saveCareReminder(null, user, command); }
    @PatchMapping("/reminders/{id}")
    public CareData.Reminder reminder(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody CareData.ReminderCommand command) { return journal.saveCareReminder(id, user, command); }
    @PostMapping("/reminders/{id}/{action}")
    public void action(@PathVariable Integer id, @PathVariable String action, @AuthenticationPrincipal AuthenticatedUser user, @RequestBody CareData.ReminderAction command) { journal.actCareReminder(id, user, action, command); }
    @DeleteMapping("/reminders/{id}")
    public void deleteReminder(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser user) { journal.deleteCareReminder(id, user); }
}
