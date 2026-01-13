package ru.growerhub.backend.journal.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;

@Service
public class JournalService {
    private final PlantJournalEntryRepository entryRepository;
    private final PlantJournalPhotoRepository photoRepository;

    public JournalService(
            PlantJournalEntryRepository entryRepository,
            PlantJournalPhotoRepository photoRepository
    ) {
        this.entryRepository = entryRepository;
        this.photoRepository = photoRepository;
    }

    @Transactional
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
            entry.setWateringDetails(details);
            entryRepository.save(entry);
        }
    }

    @Transactional
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

    @Transactional
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

    private String buildWateringText(int durationS, double waterVolumeL, Double ph, String fertilizersPerLiter) {
        List<String> parts = new ArrayList<>();
        parts.add(String.format(java.util.Locale.US, "obem_vody=%.2fl", waterVolumeL));
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
}

