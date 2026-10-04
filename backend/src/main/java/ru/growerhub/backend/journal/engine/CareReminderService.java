package ru.growerhub.backend.journal.engine;

import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.CareSettings;
import ru.growerhub.backend.common.contract.*;
import ru.growerhub.backend.journal.contract.CareData;
import ru.growerhub.backend.journal.jpa.*;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.user.UserFacade;

@Service
public class CareReminderService {
    private final CareReminderRepository repository;
    private final CareService care;
    private final PlantFacade plants;
    private final UserFacade users;
    private final CareSettings settings;
    public CareReminderService(CareReminderRepository repository, CareService care,
            @Lazy PlantFacade plants, @Lazy UserFacade users, CareSettings settings) {
        this.repository = repository; this.care = care; this.plants = plants; this.users = users; this.settings = settings;
    }
    public List<CareData.Reminder> list(AuthenticatedUser user) {
        var names = new HashMap<Integer, String>();
        plants.listPlants(user).forEach(p -> names.put(p.id(), p.name()));
        return repository.findByUserIdAndEnabledTrueOrderByDueAtAsc(user.id()).stream()
                .filter(r -> names.containsKey(r.plantId)).map(r -> view(r, names.get(r.plantId))).toList();
    }
    public CareData.Reminder save(Integer id, AuthenticatedUser user, CareData.ReminderCommand command) {
        care.lock(user);
        var plant = plants.requireOwnedPlantInfo(command.plantId(), user);
        if (command.dueAt() == null || command.title() == null || command.title().isBlank()
                || command.title().length() > 200 || !CareService.ACTIONS.contains(Objects.toString(command.action(), ""))
                || (command.repeatDays() != null && (command.repeatDays() < 1 || command.repeatDays() > settings.maxRepeatDays()))
                || !Set.of("calendar", "completion").contains(Objects.toString(command.repeatMode(), ""))) {
            throw new DomainException("unprocessable", "Проверьте дату, название и повтор напоминания");
        }
        var r = id == null ? new CareReminderEntity() : owned(id, user);
        if (id == null && repository.countByUserIdAndEnabledTrue(user.id()) >= settings.maxReminders()) {
            throw new DomainException("conflict", "Достигнут предел активных напоминаний");
        }
        r.userId = user.id(); r.plantId = plant.id(); r.action = command.action(); r.title = command.title().trim();
        r.dueAt = command.dueAt(); r.repeatDays = command.repeatDays(); r.repeatMode = command.repeatMode();
        r.occurrenceKey = UUID.randomUUID().toString().replace("-", ""); r.enabled = true;
        return view(repository.save(r), plant.name());
    }
    public void act(Integer id, AuthenticatedUser user, String action, CareData.ReminderAction command) {
        care.lock(user);
        var r = owned(id, user);
        plants.requireOwnedPlantInfo(r.plantId, user);
        if (!r.enabled || !Objects.equals(r.occurrenceKey, command.occurrenceKey())) return;
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (action.equals("snooze")) {
            if (command.dueAt() == null || !command.dueAt().isAfter(now)) throw new DomainException("unprocessable", "Укажите будущую дату");
            r.dueAt = command.dueAt();
        } else if (action.equals("done") || action.equals("skip")) {
            if (action.equals("done")) care.create(r.plantId, user,
                    new CareData.EntryCommand(r.action, r.title, now, "reminder:" + r.occurrenceKey));
            if (r.repeatDays == null) r.enabled = false;
            else {
                var profile = users.getUser(user.id());
                ZoneId zone = profile == null ? ZoneOffset.UTC : ZoneId.of(profile.timezone());
                var next = (r.repeatMode.equals("completion") ? now : r.dueAt).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone);
                do { next = next.plusDays(r.repeatDays); } while (!next.toInstant().isAfter(now.toInstant(ZoneOffset.UTC)));
                r.dueAt = LocalDateTime.ofInstant(next.toInstant(), ZoneOffset.UTC);
            }
        } else throw new DomainException("unprocessable", "Неизвестное действие");
        r.occurrenceKey = UUID.randomUUID().toString().replace("-", "");
        repository.save(r);
    }
    public void delete(Integer id, AuthenticatedUser user) { care.lock(user); repository.delete(owned(id, user)); }
    private CareReminderEntity owned(Integer id, AuthenticatedUser user) {
        var r = repository.findById(id).orElseThrow(() -> new DomainException("not_found", "Напоминание не найдено"));
        if (!r.userId.equals(user.id())) throw new DomainException("not_found", "Напоминание не найдено");
        return r;
    }
    private CareData.Reminder view(CareReminderEntity r, String name) {
        return new CareData.Reminder(r.id, r.plantId, name, r.action, r.title, r.dueAt,
                r.repeatDays, r.repeatMode, r.occurrenceKey, r.enabled);
    }
}
