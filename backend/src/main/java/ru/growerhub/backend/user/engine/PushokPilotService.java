package ru.growerhub.backend.user.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.UserSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.user.contract.PushokPilot;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@Service
public class PushokPilotService {
    private final UserRepository users;
    private final UserSettings settings;

    public PushokPilotService(UserRepository users, UserSettings settings) {
        this.users = users;
        this.settings = settings;
    }

    public PushokPilot.Request get(Integer userId) {
        return toRequest(requireAccount(users.findById(userId).orElse(null)));
    }

    public PushokPilot.Request save(Integer userId, PushokPilot.ContactMethod method, String contact, String equipment) {
        UserEntity user = requireAccount(users.lockById(userId).orElse(null));
        String value = contact == null ? "" : contact.strip();
        String note = equipment == null ? "" : equipment.strip();
        if (method == null || value.isBlank() || value.length() > settings.getPilotContactMaxLength()
                || note.length() > settings.getPilotEquipmentMaxLength() || value.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException("bad_request", "Ukazhite sposob svjazi i korrektnyj kontakt");
        }
        if (method == PushokPilot.ContactMethod.EMAIL && !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new DomainException("bad_request", "Ukazhite korrektnyj email");
        }
        if (method == PushokPilot.ContactMethod.TELEGRAM
                && !value.matches("(?:@|https://t\\.me/)?[A-Za-z0-9_]{5,32}/?")) {
            throw new DomainException("bad_request", "Ukazhite Telegram username ili ssylku t.me");
        }
        if (method.name().equals(user.getPilotContactMethod()) && value.equals(user.getPilotContact())
                && Objects.equals(note, user.getPilotEquipment())) return toRequest(user);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        user.setPilotContactMethod(method.name());
        user.setPilotContact(value);
        user.setPilotEquipment(note);
        user.setPilotRequestedAt(now);
        user.setPilotContactedAt(null);
        user.setUpdatedAt(now);
        users.save(user);
        return toRequest(user);
    }

    public void withdraw(Integer userId) {
        UserEntity user = requireAccount(users.lockById(userId).orElse(null));
        user.setPilotContactMethod(null);
        user.setPilotContact(null);
        user.setPilotEquipment(null);
        user.setPilotRequestedAt(null);
        user.setPilotContactedAt(null);
        user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        users.save(user);
    }

    public List<PushokPilot.Entry> list() {
        return users.findByPilotRequestedAtIsNotNull().stream()
                .filter(user -> !user.isDemo())
                .sorted(Comparator.comparing(UserEntity::getPilotRequestedAt).reversed())
                .map(user -> new PushokPilot.Entry(user.getId(), user.getUsername(), user.getEmail(), toRequest(user)))
                .toList();
    }

    public PushokPilot.Request markContacted(Integer userId) {
        UserEntity user = requireAccount(users.lockById(userId).orElse(null));
        if (user.getPilotRequestedAt() == null) throw new DomainException("not_found", "Zajavka ne najdena");
        if (user.getPilotContactedAt() == null) {
            user.setPilotContactedAt(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
            users.save(user);
        }
        return toRequest(user);
    }

    private UserEntity requireAccount(UserEntity user) {
        if (user == null) throw new DomainException("not_found", "Polzovatel' ne najden");
        if (user.isDemo() || !user.isActive()) throw new DomainException("forbidden", "Trebuetsja aktivnyj akkaunt");
        return user;
    }

    private PushokPilot.Request toRequest(UserEntity user) {
        if (user.getPilotRequestedAt() == null) return null;
        return new PushokPilot.Request(PushokPilot.ContactMethod.valueOf(user.getPilotContactMethod()),
                user.getPilotContact(), user.getPilotEquipment(), user.getPilotRequestedAt(), user.getPilotContactedAt());
    }
}
