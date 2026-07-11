package ru.growerhub.backend.journal.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsRepository;

@Service
public class JournalService {
    private final PlantJournalEntryRepository entryRepository;
    private final PlantJournalPhotoRepository photoRepository;
    private final PlantJournalWateringDetailsRepository wateringDetailsRepository;

    public JournalService(
            PlantJournalEntryRepository entryRepository,
            PlantJournalPhotoRepository photoRepository,
            PlantJournalWateringDetailsRepository wateringDetailsRepository
    ) {
        this.entryRepository = entryRepository;
        this.photoRepository = photoRepository;
        this.wateringDetailsRepository = wateringDetailsRepository;
    }

    public void createWateringEntries(
            List<WateringTarget> targets,
            Integer userId,
            LocalDateTime eventAt,
            Double ph,
            String fertilizersPerLiter
    ) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (WateringTarget target : targets) {
            if (target.plantId() == null) {
                continue;
            }
            PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
            entry.setPlantId(target.plantId());
            entry.setUserId(userId);
            entry.setType("watering");
            entry.setText(buildWateringText(target.durationS(), target.waterVolumeL(), ph, fertilizersPerLiter));
            entry.setEventAt(eventAt);
            PlantJournalWateringDetailsEntity details = PlantJournalWateringDetailsEntity.create();
            details.setJournalEntry(entry);
            details.setWaterVolumeL(target.waterVolumeL());
            details.setDurationS(target.durationS());
            details.setPh(ph);
            details.setFertilizersPerLiter(fertilizersPerLiter);
            details.setPlantId(target.plantId());
            entry.setWateringDetails(details);
            entryRepository.save(entry);
        }
    }

    public void createSessionWateringEntries(
            List<SessionWateringTarget> targets,
            LocalDateTime eventAt,
            Double ph,
            String fertilizersPerLiter
    ) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        for (SessionWateringTarget target : targets) {
            if (target == null || target.sessionId() == null || target.plantId() == null) {
                continue;
            }
            if (wateringDetailsRepository.existsByPumpSessionIdAndPlantId(target.sessionId(), target.plantId())) {
                continue;
            }
            PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
            entry.setPlantId(target.plantId());
            entry.setUserId(target.userId());
            entry.setType("watering");
            entry.setText(buildWateringText(
                    target.durationS(),
                    target.waterVolumeL(),
                    ph,
                    fertilizersPerLiter
            ));
            entry.setEventAt(eventAt);
            entry.setCreatedAt(createdAt);
            entry.setUpdatedAt(createdAt);

            PlantJournalWateringDetailsEntity details = PlantJournalWateringDetailsEntity.create();
            details.setJournalEntry(entry);
            details.setWaterVolumeL(target.waterVolumeL());
            details.setDurationS(target.durationS());
            details.setPh(ph);
            details.setFertilizersPerLiter(fertilizersPerLiter);
            details.setPumpSessionId(target.sessionId());
            details.setPlantId(target.plantId());
            details.setMode(target.mode());
            details.setCompletionReason(target.completionReason());
            entry.setWateringDetails(details);
            entryRepository.save(entry);
        }
    }

    public PlantJournalEntryEntity createEntry(
            Integer plantId,
            Integer userId,
            String type,
            String text,
            LocalDateTime eventAt,
            List<String> photoUrls
    ) {
        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlantId(plantId);
        entry.setUserId(userId);
        entry.setType(type);
        entry.setText(text);
        entry.setEventAt(eventAt);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        entryRepository.save(entry);

        List<String> urls = photoUrls != null ? photoUrls : List.of();
        if (!urls.isEmpty()) {
            List<PlantJournalPhotoEntity> photos = new ArrayList<>();
            for (String url : urls) {
                PlantJournalPhotoEntity photo = PlantJournalPhotoEntity.create();
                photo.setJournalEntry(entry);
                photo.setUrl(url);
                photos.add(photo);
            }
            photoRepository.saveAll(photos);
        }
        return entry;
    }

    public PlantJournalEntryEntity updateEntry(PlantJournalEntryEntity entry, String type, String text) {
        if (type != null) {
            entry.setType(type);
        }
        if (text != null || entry.getText() != null) {
            entry.setText(text);
        }
        entry.setUpdatedAt(LocalDateTime.now());
        return entryRepository.save(entry);
    }

    private String buildWateringText(int durationS, Double waterVolumeL, Double ph, String fertilizersPerLiter) {
        List<String> parts = new ArrayList<>();
        if (waterVolumeL != null) {
            parts.add(String.format(java.util.Locale.US, "obem_vody=%.3fl", waterVolumeL));
        } else {
            parts.add("obem_vody=ne_rasschitan");
        }
        parts.add("dlitelnost=" + durationS + "s");
        if (ph != null) {
            parts.add("ph=" + ph);
        }
        if (fertilizersPerLiter != null && !fertilizersPerLiter.isEmpty()) {
            parts.add("udobreniya_na_litr=" + fertilizersPerLiter + " (udobreniya ukazany na litr)");
        }
        return String.join("; ", parts);
    }

    public record WateringTarget(Integer plantId, int durationS, double waterVolumeL) {
    }

    public record SessionWateringTarget(
            Long sessionId,
            Integer plantId,
            Integer userId,
            int durationS,
            Double waterVolumeL,
            String mode,
            String completionReason
    ) {
    }
}
