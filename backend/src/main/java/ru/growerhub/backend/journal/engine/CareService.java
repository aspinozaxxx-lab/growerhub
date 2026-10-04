package ru.growerhub.backend.journal.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.CareSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.journal.contract.CareData;
import ru.growerhub.backend.journal.contract.JournalEntry;
import ru.growerhub.backend.journal.contract.JournalPhoto;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalPhotoRepository;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.user.UserFacade;

@Service
public class CareService {
    public static final Set<String> ACTIONS = Set.of("watering", "fertilizing", "repotting", "pruning",
            "treatment", "inspection", "photo", "note", "harvest");
    private final PlantJournalEntryRepository entries;
    private final PlantJournalPhotoRepository photos;
    private final JournalService journal;
    private final PlantFacade plants;
    private final UserFacade users;
    private final CareSettings settings;

    public CareService(PlantJournalEntryRepository entries, PlantJournalPhotoRepository photos,
            JournalService journal, @Lazy PlantFacade plants, @Lazy UserFacade users, CareSettings settings) {
        this.entries = entries; this.photos = photos; this.journal = journal;
        this.plants = plants; this.users = users; this.settings = settings;
    }

    public CareData.Page search(AuthenticatedUser user, Integer plantId, String query, String action,
            LocalDateTime from, LocalDateTime until, int page) {
        var owned = plants.listPlants(user);
        if (plantId != null) plants.requireOwnedPlantInfo(plantId, user);
        if (page < 0) throw new DomainException("unprocessable", "Некорректная страница");
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() > settings.maxTextLength()) throw new DomainException("unprocessable", "Слишком длинный запрос");
        String kind = action == null ? "" : action;
        if (!kind.isEmpty() && !ACTIONS.contains(kind) && !kind.equals("automatic")) {
            throw new DomainException("unprocessable", "Неизвестный вид ухода");
        }
        Map<Integer, String> names = new LinkedHashMap<>();
        owned.forEach(p -> names.put(p.id(), p.name()));
        var matches = owned.stream().filter(p -> (p.name() + " " + Objects.toString(p.strain(), "")
                + " " + Objects.toString(p.locationLabel(), "")).toLowerCase(Locale.ROOT).contains(q))
                .map(PlantInfo::id).toList();
        String pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var slice = entries.searchCare(user.id(), plantId == null ? -1 : plantId, pattern, kind,
                matches.isEmpty() ? List.of(-1) : matches,
                from == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : from,
                until == null ? LocalDateTime.of(9999, 1, 1, 0, 0) : until,
                PageRequest.of(page, settings.pageSize()));
        return new CareData.Page(slice.getContent().stream().filter(e -> names.containsKey(e.getPlantId()))
                .map(e -> item(e, names.get(e.getPlantId()))).toList(), slice.hasNext());
    }

    public List<CareData.Summary> summaries(AuthenticatedUser user) {
        Map<Integer, Integer> cover = new LinkedHashMap<>();
        for (Object[] row : photos.findCovers(user.id())) cover.putIfAbsent((Integer) row[0], (Integer) row[1]);
        List<CareData.Summary> result = new ArrayList<>();
        for (Object[] row : entries.careSummaries(user.id())) {
            Integer plantId = (Integer) row[0];
            result.add(new CareData.Summary(plantId, cover.get(plantId), (LocalDateTime) row[1],
                    (LocalDateTime) row[2], (Long) row[3]));
        }
        return result;
    }

    public CareData.Item create(Integer plantId, AuthenticatedUser user, CareData.EntryCommand command) {
        lock(user);
        var plant = plants.requireOwnedPlantInfo(plantId, user);
        validate(command);
        var old = entries.findByUserIdAndClientKey(user.id(), command.clientKey());
        if (old.isPresent()) {
            var value = old.get();
            if (!value.getPlantId().equals(plantId) || !Objects.equals(value.getCareAction(), command.action())
                    || !Objects.equals(value.getText(), command.text())
                    || (command.eventAt() != null && !Objects.equals(value.getEventAt(), command.eventAt()))) {
                throw new DomainException("conflict", "Этот запрос уже использован для другой записи");
            }
            return item(value, plant.name());
        }
        String type = switch (command.action()) {
            case "watering", "photo", "note", "harvest" -> command.action();
            default -> "other";
        };
        var entry = journal.createEntry(plantId, user.id(), type, command.text(),
                command.eventAt() == null ? LocalDateTime.now(ZoneOffset.UTC) : command.eventAt(), List.of());
        entry.setCareAction(command.action());
        entry.setClientKey(command.clientKey());
        entries.save(entry);
        return item(entry, plant.name());
    }

    public CareData.Item update(Integer id, AuthenticatedUser user, CareData.EntryCommand command) {
        lock(user);
        var entry = requireEntry(id, user);
        if (entry.getWateringDetails() != null) throw new DomainException("conflict", "Параметры состоявшегося полива изменять нельзя");
        if (command.action() != null) {
            if (!ACTIONS.contains(command.action())) throw new DomainException("unprocessable", "Неизвестный вид ухода");
            entry.setCareAction(command.action());
            entry.setType(Set.of("watering", "photo", "note", "harvest").contains(command.action()) ? command.action() : "other");
        }
        validateText(command.text());
        entry.setText(command.text());
        if (command.eventAt() != null) entry.setEventAt(command.eventAt());
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entries.save(entry);
        return item(entry, plants.requireOwnedPlantInfo(entry.getPlantId(), user).name());
    }

    public void delete(Integer id, AuthenticatedUser user) {
        lock(user);
        var entry = requireEntry(id, user);
        if (entry.getWateringDetails() != null) throw new DomainException("conflict", "Историю работы оборудования удалять нельзя");
        photos.deleteAllByJournalEntry_Id(id);
        entries.delete(entry);
    }

    public JournalPhoto addPhoto(Integer id, AuthenticatedUser user, byte[] jpeg) {
        lock(user);
        var entry = requireEntry(id, user);
        if (entry.getWateringDetails() != null) throw new DomainException("conflict", "Добавьте отдельную запись с фотографией");
        if (jpeg == null || jpeg.length == 0 || jpeg.length > settings.maxUploadBytes()) {
            throw new DomainException("unprocessable", "Фотография слишком большая");
        }
        String hash;
        try { hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(jpeg)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        var existing = photos.findByJournalEntry_IdAndContentHash(id, hash);
        if (existing.isPresent()) return photoView(existing.get());
        if (photos.countByJournalEntry_Id(id) >= settings.maxPhotos()) {
            throw new DomainException("conflict", "Достигнут предел фотографий в одной записи");
        }
        if (photos.countOwned(user.id()) >= settings.maxAccountPhotos()) throw new DomainException("conflict", "Достигнут предел фотографий аккаунта");
        var photo = PlantJournalPhotoEntity.create();
        photo.setJournalEntry(entry); photo.setData(jpeg); photo.setContentType("image/jpeg"); photo.setContentHash(hash);
        return photoView(photos.save(photo));
    }

    public void deletePhoto(Integer photoId, AuthenticatedUser user) {
        lock(user);
        var photo = photos.findById(photoId).orElseThrow(() -> new DomainException("not_found", "Фотография не найдена"));
        var entry = requireEntry(photo.getJournalEntry().getId(), user);
        if (entry.getWateringDetails() != null) throw new DomainException("conflict", "Запись оборудования доступна только для чтения");
        photos.delete(photo);
    }

    public PlantJournalEntryEntity requireEntry(Integer id, AuthenticatedUser user) {
        var entry = entries.findById(id).orElseThrow(() -> new DomainException("not_found", "Запись не найдена"));
        plants.requireOwnedPlantInfo(entry.getPlantId(), user);
        if (!Objects.equals(user.id(), entry.getUserId())) throw new DomainException("not_found", "Запись не найдена");
        return entry;
    }

    public CareData.Item item(PlantJournalEntryEntity entry, String plantName) {
        JournalEntry value = journal.view(entry);
        return new CareData.Item(value, plantName, entry.getCareAction(), value.wateringDetails() == null);
    }

    private JournalPhoto photoView(PlantJournalPhotoEntity photo) {
        return new JournalPhoto(photo.getId(), photo.getUrl(), photo.getCaption(), photo.getData() != null);
    }

    private void validate(CareData.EntryCommand command) {
        if (command == null || !ACTIONS.contains(Objects.toString(command.action(), ""))) {
            throw new DomainException("unprocessable", "Выберите вид ухода");
        }
        validateText(command.text());
        if (command.clientKey() == null || !command.clientKey().matches("[a-zA-Z0-9:_-]{1,128}")) {
            throw new DomainException("unprocessable", "Отсутствует идентификатор записи");
        }
    }

    private void validateText(String value) {
        if (value != null && value.length() > settings.maxTextLength()) {
            throw new DomainException("unprocessable", "Заметка слишком длинная");
        }
    }

    public void lock(AuthenticatedUser user) {
        if (user.isDemo()) users.lockDemoOwner(user.id()); else users.lockAccount(user.id());
    }
}
