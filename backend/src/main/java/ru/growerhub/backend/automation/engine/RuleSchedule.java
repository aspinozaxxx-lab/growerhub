package ru.growerhub.backend.automation.engine;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import ru.growerhub.backend.automation.contract.RuleData.NextRun;
import ru.growerhub.backend.common.config.automation.RuleSettings;
import ru.growerhub.backend.common.contract.DomainException;

final class RuleSchedule {
    private final RuleSettings settings;
    RuleSchedule(RuleSettings settings) { this.settings = settings; }
    record Occurrence(String key, Instant at) {}

    void validate(JsonNode t, int version) {
        String type = t.path("type").asText();
        switch (type) {
            case "schedule" -> {
                fields(t, version, "type", "at", "days"); time(t, "at"); days(t);
            }
            case "interval" -> {
                fields(t, version, "type", "seconds", "anchor");
                bounded(t, "seconds", settings.minIntervalSeconds(), settings.maxIntervalSeconds());
                try { Instant.parse(t.path("anchor").asText()); } catch (DateTimeException ex) { throw bad("Укажите дату первого запуска в UTC"); }
            }
            case "calendar" -> {
                if (version < 2) throw bad("Для календарного повторения используйте версию правила 2");
                fields(t, version, "type", "every", "unit", "startDate", "at", "timezone");
                if (!Set.of("day", "week").contains(t.path("unit").asText())) throw bad("Выберите повторение в днях или неделях");
                int scale = t.path("unit").asText().equals("week") ? 604800 : 86400;
                bounded(t, "every", 1, settings.maxIntervalSeconds() / scale);
                date(t); time(t, "at"); zone(t, ZoneOffset.UTC);
            }
            default -> throw bad("Неизвестный способ запуска");
        }
        if (version >= 2 && t.has("misfire") && !Set.of("skip", "run_once").contains(t.path("misfire").asText()))
            throw bad("Выберите поведение при пропуске: skip или run_once");
    }

    Occurrence current(JsonNode t, int version, Instant now, ZoneId accountZone) {
        Occurrence result = latest(t, now, accountZone);
        if (result == null) return null;
        if (version == 1 && t.path("type").asText().equals("schedule")
                && !now.atZone(accountZone).toLocalTime().withSecond(0).withNano(0).equals(time(t, "at"))) return null;
        if (version >= 2 && !t.path("misfire").asText("skip").equals("run_once")
                && Duration.between(result.at(), now).getSeconds() > settings.scheduleGraceSeconds()) return null;
        return result;
    }

    List<NextRun> next(JsonNode t, int version, Instant now, ZoneId accountZone, int count) {
        if (t.path("type").asText().equals("change")) return List.of();
        ZoneId zone = t.path("type").asText().equals("calendar") ? zone(t, accountZone) : accountZone;
        List<NextRun> result = new ArrayList<>();
        Instant cursor = now;
        var due = current(t, version, now, accountZone);
        if (due != null) {
            result.add(new NextRun(due.at(), due.at().atZone(zone).toString()));
            cursor = due.at().plusSeconds(1);
        }
        while (result.size() < count) {
            Instant at = after(t, cursor, accountZone);
            if (at == null) break;
            result.add(new NextRun(at, at.atZone(zone).toString())); cursor = at.plusSeconds(1);
        }
        return List.copyOf(result);
    }

    private Occurrence latest(JsonNode t, Instant now, ZoneId accountZone) {
        return switch (t.path("type").asText()) {
            case "interval" -> {
                Instant anchor = Instant.parse(t.get("anchor").asText()); long elapsed = Duration.between(anchor, now).getSeconds();
                if (elapsed < 0) yield null;
                long n = elapsed / t.get("seconds").asLong();
                yield new Occurrence("interval:" + n, anchor.plusSeconds(n * t.get("seconds").asLong()));
            }
            case "calendar" -> {
                ZoneId zone = zone(t, accountZone); LocalDate start = date(t), today = now.atZone(zone).toLocalDate();
                long step = periodDays(t), elapsed = ChronoUnit.DAYS.between(start, today);
                if (elapsed < 0) yield null;
                long n = elapsed / step;
                Occurrence found = null;
                for (int i = 0; i < 4 && n >= 0; i++, n--) {
                    LocalDate day = start.plusDays(n * step); Instant at = instant(day, time(t, "at"), zone);
                    if (at != null && !at.isAfter(now)) { found = new Occurrence("calendar:" + day + ":" + t.get("at").asText(), at); break; }
                }
                yield found;
            }
            case "schedule" -> {
                LocalDate today = now.atZone(accountZone).toLocalDate(); Occurrence found = null;
                for (int i = 0; i <= 7; i++) {
                    LocalDate day = today.minusDays(i);
                    if (!days(t).contains(day.getDayOfWeek().getValue())) continue;
                    Instant at = instant(day, time(t, "at"), accountZone);
                    if (at != null && !at.isAfter(now)) { found = new Occurrence("schedule:" + day + ":" + t.get("at").asText(), at); break; }
                }
                yield found;
            }
            default -> null;
        };
    }

    private Instant after(JsonNode t, Instant cursor, ZoneId accountZone) {
        if (t.path("type").asText().equals("interval")) {
            Instant anchor = Instant.parse(t.get("anchor").asText());
            long elapsed = Duration.between(anchor, cursor).getSeconds(), seconds = t.get("seconds").asLong();
            return elapsed <= 0 ? anchor : anchor.plusSeconds(Math.floorDiv(elapsed + seconds - 1, seconds) * seconds);
        }
        boolean calendar = t.path("type").asText().equals("calendar");
        ZoneId zone = calendar ? zone(t, accountZone) : accountZone;
        LocalDate today = cursor.atZone(zone).toLocalDate(), start = calendar ? date(t) : today;
        long step = calendar ? periodDays(t) : 1;
        long n = Math.max(0, Math.floorDiv(ChronoUnit.DAYS.between(start, today), step));
        for (int i = 0; i < 16; i++, n++) {
            LocalDate day = start.plusDays(n * step);
            if (!calendar && !days(t).contains(day.getDayOfWeek().getValue())) continue;
            Instant at = instant(day, time(t, "at"), zone);
            if (at != null && !at.isBefore(cursor)) return at;
        }
        return null;
    }

    private long periodDays(JsonNode t) { return t.get("every").asLong() * (t.get("unit").asText().equals("week") ? 7 : 1); }
    private Instant instant(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime local = date.atTime(time); var offsets = zone.getRules().getValidOffsets(local);
        return offsets.isEmpty() ? null : local.toInstant(offsets.getFirst());
    }
    private LocalDate date(JsonNode t) {
        try { return LocalDate.parse(t.path("startDate").asText()); } catch (DateTimeException ex) { throw bad("Укажите дату первого запуска ГГГГ-ММ-ДД"); }
    }
    private ZoneId zone(JsonNode t, ZoneId fallback) {
        try { return t.has("timezone") ? ZoneId.of(t.get("timezone").asText()) : fallback; } catch (DateTimeException ex) { throw bad("Укажите действующий часовой пояс"); }
    }
    private LocalTime time(JsonNode t, String key) {
        try { String s = t.path(key).asText(); if (!s.matches("\\d{2}:\\d{2}")) throw new DateTimeException("time"); return LocalTime.parse(s); }
        catch (DateTimeException ex) { throw bad("Укажите время ЧЧ:ММ"); }
    }
    private Set<Integer> days(JsonNode t) {
        if (!t.has("days")) return Set.of(1, 2, 3, 4, 5, 6, 7);
        if (!t.get("days").isArray() || t.get("days").isEmpty()) throw bad("Выберите дни недели");
        Set<Integer> result = new HashSet<>();
        for (var day : t.get("days")) { if (!day.isIntegralNumber() || day.asInt() < 1 || day.asInt() > 7) throw bad("День недели: от 1 до 7"); result.add(day.asInt()); }
        return result;
    }
    private int bounded(JsonNode t, String key, int min, int max) {
        if (!t.path(key).isIntegralNumber() || !t.get(key).canConvertToInt() || t.get(key).asInt() < min || t.get(key).asInt() > max)
            throw bad(key + ": допустимо от " + min + " до " + max);
        return t.get(key).asInt();
    }
    private void fields(JsonNode t, int version, String... keys) {
        Set<String> allowed = new HashSet<>(Arrays.asList(keys)); if (version >= 2) allowed.add("misfire");
        t.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw bad("Неизвестное поле расписания: " + k); });
    }
    private DomainException bad(String message) { return new DomainException("unprocessable", message); }
}
