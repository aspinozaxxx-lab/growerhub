package ru.growerhub.backend.automation.engine;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.automation.contract.WateringPlanData;
import ru.growerhub.backend.automation.contract.WeatherForecastData;
import ru.growerhub.backend.common.config.automation.WateringPlanSettings;
import ru.growerhub.backend.common.config.automation.WeatherSettings;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.pump.contract.PumpSessionData;

@Service
public class WateringPlanEngine {
    private final WateringPlanSettings settings;
    private final WeatherSettings weatherSettings;

    public WateringPlanEngine(WateringPlanSettings settings, WeatherSettings weatherSettings) {
        this.settings = settings; this.weatherSettings = weatherSettings;
    }

    public boolean supports(Map<String, Object> cfg) { return cfg.containsKey("trigger_mode"); }

    public Map<String, Object> defaults() {
        var result = new java.util.LinkedHashMap<String, Object>(Map.of("observe_only", true, "schedule_time", settings.defaultTime(),
                "schedule_days", List.of(1, 2, 3, 4, 5, 6, 7), "window_start", settings.defaultTime(),
                "window_end", settings.defaultWindowEnd(), "min_interval_hours", settings.defaultMinIntervalHours(),
                "daily_max_seconds", settings.defaultDailyMaxSeconds()));
        result.put("weather_enabled", weatherSettings.defaultEnabled()); result.put("rain_exposure", weatherSettings.defaultExposure());
        result.put("rain_threshold_mm", weatherSettings.defaultRainMm());
        result.put("rain_max_delay_hours", weatherSettings.defaultDelayHours());
        result.put("weather_unavailable_policy", weatherSettings.defaultUnavailablePolicy());
        return result;
    }

    public void validate(Map<String, Object> cfg) {
        if (!supports(cfg)) return;
        String mode = String.valueOf(cfg.get("trigger_mode"));
        if (!List.of("schedule", "drying", "adaptive").contains(mode)) fail("Неизвестный режим запуска полива");
        parseTime(cfg, "schedule_time"); parseTime(cfg, "window_start"); parseTime(cfg, "window_end");
        if (!(cfg.get("schedule_days") instanceof List<?> days) || days.isEmpty()
                || days.stream().anyMatch(day -> !(day instanceof Number n) || n.doubleValue() != n.intValue()
                        || n.intValue() < 1 || n.intValue() > 7)) fail("Выберите дни недели для полива");
        if (!(cfg.get("observe_only") instanceof Boolean)) fail("Укажите режим наблюдения или управления");
        if (cfg.containsKey("weather_enabled") && !(cfg.get("weather_enabled") instanceof Boolean)) fail("Укажите, учитывать ли погоду");
        if (Boolean.TRUE.equals(cfg.get("weather_enabled"))) {
            if (!List.of("outdoors", "roof").contains(cfg.get("rain_exposure"))) fail("Укажите, попадает ли дождь на участок");
            if (!List.of("pause", "base").contains(cfg.get("weather_unavailable_policy"))) fail("Выберите правило при недоступном прогнозе");
            positiveInteger(cfg, "rain_max_delay_hours");
            if (num(cfg, "rain_max_delay_hours") > weatherSettings.maximumDelayHours()) fail("Слишком большая отсрочка полива");
            if (!Double.isFinite(num(cfg, "rain_threshold_mm")) || num(cfg, "rain_threshold_mm") <= 0) fail("Укажите положительный порог ожидаемых осадков");
            if ("schedule".equals(mode) && "outdoors".equals(cfg.get("rain_exposure"))) {
                LocalTime time = parseTime(cfg, "schedule_time"), start = parseTime(cfg, "window_start"), end = parseTime(cfg, "window_end");
                if (!insideWindow(time, start, end)) fail("Время по расписанию должно попадать в разрешённое окно переноса");
            }
        }
        positiveInteger(cfg, "run_seconds"); positiveInteger(cfg, "daily_max_seconds");
        positiveInteger(cfg, "min_interval_hours");
        if (num(cfg, "run_seconds") > num(cfg, "daily_max_seconds")) fail("Доза превышает дневной лимит");
        if (!"fixed_duration".equals(cfg.get("stop_mode"))) fail("Для нового режима выберите полив по времени");
        if (Boolean.TRUE.equals(cfg.get("pulse_enabled"))) {
            if (!Double.isFinite(num(cfg, "pulse_run_minutes")) || !Double.isFinite(num(cfg, "pulse_pause_minutes"))
                    || num(cfg, "pulse_run_minutes") * 60 < 1 || num(cfg, "pulse_pause_minutes") * 60 < 1
                    || num(cfg, "pulse_run_minutes") * 60 > Integer.MAX_VALUE || num(cfg, "pulse_pause_minutes") * 60 > Integer.MAX_VALUE)
                fail("Интервалы импульсов должны быть больше нуля");
        }
        if (cfg.containsKey("wet_anchor") || cfg.containsKey("dry_anchor")) {
            double wet = num(cfg, "wet_anchor"), dry = num(cfg, "dry_anchor");
            if (!Double.isFinite(wet) || !Double.isFinite(dry)
                    || Math.abs(wet - dry) < settings.minimumAnchorRange()) fail("Ориентиры датчика слишком близки");
        }
    }

    public WateringPlanData.Plan evaluate(Integer boxId, Map<String, Object> cfg, LocalDateTime now, String timezone,
            boolean enabled, String equipmentIssue, String bindingKey, List<WateringPlanData.Point> history,
            PumpSessionData.View lastWatering, long usedToday, boolean wateringActive, String consumedKey,
            WeatherForecastData.Input weatherInput) {
        String mode = String.valueOf(cfg.get("trigger_mode"));
        ZoneId zone = ZoneId.of(timezone);
        boolean observe = !Boolean.FALSE.equals(cfg.get("observe_only"));
        int run = (int) num(cfg, "run_seconds");
        List<String> reasons = new ArrayList<>();
        LocalDateTime last = lastWatering == null ? null : lastWatering.finishedAt();
        LocalDateTime dueAt = null;
        String key = null;
        String status = "waiting";
        WateringPlanData.Soil soil = null;
        var pending = weatherInput == null ? null : weatherInput.pending();
        if (pending != null && (!Objects.equals(pending.fingerprint(), weatherInput.fingerprint())
                || Objects.equals(pending.key(), consumedKey)
                || last != null && !last.isBefore(pending.originalAt()))) pending = null;
        if ("schedule".equals(mode)) {
            LocalTime time = parseTime(cfg, "schedule_time");
            List<?> days = (List<?>) cfg.get("schedule_days");
            LocalDate day = now.toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate();
            for (int offset = 0; offset <= 7; offset++) {
                LocalDate date = day.plusDays(offset);
                if (!days.stream().anyMatch(d -> ((Number) d).intValue() == date.getDayOfWeek().getValue())) continue;
                // V DST gap propuskaem slot; v overlap vybran pervyj offset, odin lokalnyj klyuch.
                var offsets = zone.getRules().getValidOffsets(date.atTime(time));
                if (offsets.isEmpty()) continue;
                LocalDateTime candidate = LocalDateTime.ofInstant(date.atTime(time).toInstant(offsets.getFirst()), ZoneOffset.UTC);
                String candidateKey = "watering:" + boxId + ":schedule:" + date + ":" + time;
                if (candidate.plusSeconds(settings.executionWindowSeconds()).isBefore(now)
                        || Objects.equals(candidateKey, consumedKey)) continue;
                dueAt = candidate; key = candidateKey; break;
            }
            reasons.add("По выбранным дням и времени; пропущенный полив не догоняется");
            if (pending != null && now.isBefore(pending.deadline())) {
                dueAt = nextWindow(pending.originalAt().isAfter(now) ? pending.originalAt() : now, zone, cfg); key = pending.key();
            } else if (pending != null) reasons.add("Срок ожидания погоды для предыдущего слота истёк; он пропущен без догоняющего полива");
        } else {
            soil = analyze(cfg, bindingKey, history, now, lastWatering);
            if (soil.issue() != null) { status = "unready"; reasons.add(soil.issue()); }
            else {
                double remaining = Math.max(0, 1 - soil.dryingFraction());
                if (soil.dryingPerHour() > settings.minimumDryingPerHour()) {
                    double predictedHours = remaining / soil.dryingPerHour();
                    LocalDateTime estimate = now.plusSeconds((long) Math.ceil(Math.min(predictedHours, settings.historyHours()) * 3600));
                    if ("drying".equals(mode) && remaining > 0) {
                        reasons.add("Местный ориентир сухой почвы ещё не достигнут");
                    } else if (predictedHours > settings.historyHours()) {
                        reasons.add("Оценка слишком далеко за пределами наблюдаемой истории; дата пока не назначена");
                    } else {
                        dueAt = nextWindow(estimate, zone, cfg);
                        key = "watering:" + boxId + ":" + mode + ":" + (lastWatering == null ? "first" : lastWatering.id())
                                + ":" + dueAt.toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate();
                        reasons.add("Расчёт по местным ориентирам и устойчивой динамике; доза фиксирована пользователем");
                    }
                } else reasons.add("Нет подтверждённого темпа высыхания; ровная линия не запускает полив");
            }
        }
        // Dinamika proverena zanovo, no smena sutok ne sozdayot novyj srok ozhidaniya togo zhe poliva.
        if (!"schedule".equals(mode) && pending != null && dueAt != null) key = pending.key();
        WeatherForecastData.Decision weather = null;
        if (Boolean.TRUE.equals(cfg.get("weather_enabled"))) {
            if ("roof".equals(cfg.get("rain_exposure"))) {
                weather = new WeatherForecastData.Decision(null, "roof", null, null, null, null, null, null,
                        String.valueOf(cfg.get("weather_unavailable_policy")), null, null);
                reasons.add("Участок под крышей: прогноз дождя не заменяет полив");
            } else if (dueAt != null && !Objects.equals(key, consumedKey)) {
                var decision = weatherDecision(cfg, weatherInput, pending, key, dueAt, now);
                weather = decision;
                if ("postponed".equals(decision.status()) || "unavailable".equals(decision.status()) || "expired".equals(decision.status())) {
                    if ("waiting".equals(status)) status = "weather_" + decision.status();
                    reasons.add(decision.issue());
                } else if ("fallback".equals(decision.status())) reasons.add("Прогноз недоступен: по вашему выбору действует основное правило полива");
                else reasons.add("Прогноз не достиг порога дождя; перед запуском проверим его снова");
            }
        }
        if (last != null) {
            LocalDateTime allowed = last.plusHours((long) num(cfg, "min_interval_hours"));
            reasons.add("Последний полив: " + last.toInstant(ZoneOffset.UTC).atZone(zone)
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")));
            if (dueAt != null && dueAt.isBefore(allowed)) {
                status = "limited"; reasons.add("Минимальный интервал ещё не прошёл");
                if (!"schedule".equals(mode)) dueAt = nextWindow(allowed, zone, cfg);
            }
        }
        if (dueAt != null && (!dueAt.isAfter(now) || dueAt.toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate().equals(now.toInstant(ZoneOffset.UTC).atZone(zone).toLocalDate()))
                && usedToday + run > num(cfg, "daily_max_seconds")) {
            status = "limited"; reasons.add("Дневной лимит не допускает выбранную дозу");
        }
        if (equipmentIssue != null) { status = "unready"; reasons.add(equipmentIssue); }
        if (wateringActive) { status = "running"; reasons.add("Полив уже идёт; второй запуск исключён"); }
        boolean due = "waiting".equals(status) && dueAt != null && !dueAt.isAfter(now)
                && !Objects.equals(key, consumedKey);
        if (observe) reasons.add("Наблюдение: команды не отправляются");
        if (!enabled) reasons.add("Сценарий выключен; показан предварительный план");
        return new WateringPlanData.Plan(2, mode, status, observe, enabled, now, dueAt, key, due && enabled && !observe,
                run, Boolean.TRUE.equals(cfg.get("pulse_enabled")), (int) (num(cfg, "pulse_run_minutes") * 60),
                (int) (num(cfg, "pulse_pause_minutes") * 60), last, usedToday, timezone, soil, weather, List.copyOf(reasons));
    }

    private WeatherForecastData.Decision weatherDecision(Map<String, Object> cfg, WeatherForecastData.Input input,
            WeatherForecastData.Pending old, String key, LocalDateTime planned, LocalDateTime now) {
        String policy = String.valueOf(cfg.get("weather_unavailable_policy"));
        var forecast = input == null ? null : input.forecast();
        var pending = old != null && Objects.equals(key, old.key()) ? old : null;
        LocalDateTime from = (planned.isAfter(now) ? planned : now).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = from.plusHours(weatherSettings.horizonHours());
        Double mm = null, probability = null;
        String issue = forecast == null ? "Прогноз недоступен" : forecast.issue();
        if (issue == null && (forecast.updatedAt() == null || forecast.updatedAt().isAfter(now)
                || forecast.updatedAt().isBefore(now.minusHours(weatherSettings.maximumForecastAgeHours())))) issue = "Модель прогноза устарела";
        if (issue == null) {
            final LocalDateTime rangeFrom = from, rangeTo = to;
            var periods = forecast.periods().stream().filter(p -> p.to().isAfter(rangeFrom) && p.from().isBefore(rangeTo)).toList();
            LocalDateTime covered = from;
            double total = 0;
            for (var period : periods) {
                if (period.from().isAfter(covered) || !Double.isFinite(period.precipitationMm()) || period.precipitationMm() < 0) {
                    issue = "В прогнозе нет полного покрытия выбранного времени"; break;
                }
                if (period.precipitationMm() > 0 && (!period.symbol().contains("rain") || period.symbol().contains("sleet"))) {
                    issue = "Осадки не подтверждены как дождь; снег и смешанные осадки не заменяют полив"; break;
                }
                total += period.precipitationMm(); covered = period.to();
                if (period.probability() != null) probability = probability == null ? period.probability() : Math.max(probability, period.probability());
            }
            if (issue == null && covered.isBefore(to)) issue = "В прогнозе нет полного покрытия выбранного времени";
            if (issue == null) {
                mm = total; from = periods.getFirst().from(); to = periods.getLast().to();
            } else probability = null;
        }
        String status = issue != null ? "base".equals(policy) ? "fallback" : "unavailable"
                : mm >= num(cfg, "rain_threshold_mm") ? "postponed" : "clear";
        boolean blocked = List.of("unavailable", "postponed").contains(status);
        if (pending == null && blocked) pending = new WeatherForecastData.Pending(input == null ? "" : input.fingerprint(),
                key, planned, planned.plusHours((long) num(cfg, "rain_max_delay_hours")));
        if (pending != null && (!now.isBefore(pending.deadline()) || !planned.isBefore(pending.deadline()))) {
            status = "expired"; issue = "Предельный срок ожидания погоды истёк; этот полив пропущен, автоматического догоняющего запуска нет";
        } else if ("postponed".equals(status)) issue = "Ожидается дождь: полив отложен в пределах выбранного срока; прогноз не является фактом полива";
        // Klyuch i ishodnyj srok sokhranyayutsya do zapuska ili istecheniya, ne sdvigayutsya kazhdym worker.
        return new WeatherForecastData.Decision(forecast == null ? null : forecast.source(), status,
                forecast == null ? null : forecast.updatedAt(), forecast == null ? null : forecast.retrievedAt(),
                mm == null ? null : from, mm == null ? null : to, mm, probability, policy, pending, issue);
    }

    private WateringPlanData.Soil analyze(Map<String, Object> cfg, String binding, List<WateringPlanData.Point> history,
            LocalDateTime now, PumpSessionData.View last) {
        List<WateringPlanData.Point> points = history.stream().filter(p -> p.ts() != null && !p.ts().isAfter(now)
                && !p.ts().isBefore(now.minusHours(settings.historyHours())) && p.value() != null && Double.isFinite(p.value()))
                .sorted(Comparator.comparing(WateringPlanData.Point::ts)).toList();
        Double current = points.isEmpty() ? null : points.getLast().value();
        LocalDateTime observed = points.isEmpty() ? null : points.getLast().ts();
        String issue = null;
        Double fraction = null, rate = null;
        boolean response = false;
        double wet = num(cfg, "wet_anchor"), dry = num(cfg, "dry_anchor");
        double range = wet - dry;
        if (binding == null || !Objects.equals(binding, cfg.get("calibration_binding_key"))) issue = "Подтвердите ориентиры для текущего датчика и его положения";
        else if (!Double.isFinite(range) || Math.abs(range) < settings.minimumAnchorRange()) issue = "Нужны местные ориентиры после полива и перед обычным поливом";
        else if (points.size() < settings.minimumSamples()) issue = "Недостаточно истории датчика для динамики";
        else if (observed.isBefore(now.minusMinutes(settings.maximumGapMinutes()))) issue = "Показания датчика устарели";
        else {
            var trend = last == null || last.finishedAt() == null ? points : points.stream()
                    .filter(p -> !p.ts().isBefore(last.finishedAt().plusMinutes(settings.soakMinutes()))).toList();
            if (trend.size() < settings.minimumSamples()
                    || Duration.between(trend.getFirst().ts(), trend.getLast().ts()).toMinutes() < settings.minimumSpanHours() * 60L)
                issue = "После полива ждём впитывания и достаточной истории высыхания";
            else {
                for (int i = 1; i < trend.size(); i++) {
                    if (Duration.between(trend.get(i - 1).ts(), trend.get(i).ts()).toMinutes() > settings.maximumGapMinutes()) {
                        issue = "В истории датчика есть большой разрыв"; break;
                    }
                    if (Math.abs(trend.get(i).value() - trend.get(i - 1).value()) > Math.abs(range) * settings.maximumJumpFraction()) {
                        issue = "Резкий скачок показаний; проверьте датчик и положение"; break;
                    }
                }
                double first = median(trend.subList(0, 3)), latest = median(trend.subList(trend.size() - 3, trend.size()));
                double noise = medianDeviation(trend, range);
                if (noise >= Math.abs(range) / 3) issue = "Шум датчика слишком велик относительно ориентиров";
                fraction = (wet - latest) / range;
                rate = (first - latest) / range / (Duration.between(trend.getFirst().ts(), trend.getLast().ts()).toSeconds() / 3600.0);
                if (last != null && last.startedAt() != null && last.finishedAt() != null
                        && PumpSessionData.PHASE_COMPLETED.equals(last.phase()) && last.activeDurationS() > 0) {
                    var before = points.stream().filter(p -> !p.ts().isAfter(last.startedAt())
                            && !p.ts().isBefore(last.startedAt().minusHours(settings.responseHours()))).toList();
                    var after = points.stream().filter(p -> !p.ts().isBefore(last.finishedAt().plusMinutes(settings.soakMinutes()))
                            && !p.ts().isAfter(last.finishedAt().plusHours(settings.responseHours()))).toList();
                    response = before.size() >= 3 && after.size() >= 3
                            && (median(after) - median(before)) / range >= settings.minimumResponseFraction();
                }
                if (!response && issue == null) issue = "Пока не подтверждён отклик датчика на обычный полив; автоматического долива нет";
            }
        }
        return new WateringPlanData.Soil(current, observed, fraction, rate, points.size(), response, issue);
    }

    private LocalDateTime nextWindow(LocalDateTime utc, ZoneId zone, Map<String, Object> cfg) {
        var local = utc.toInstant(ZoneOffset.UTC).atZone(zone);
        LocalTime start = parseTime(cfg, "window_start"), end = parseTime(cfg, "window_end");
        if (start.equals(end)) return utc;
        LocalTime time = local.toLocalTime();
        boolean inside = insideWindow(time, start, end);
        if (inside) return utc;
        LocalDate date = local.toLocalDate();
        if (start.isBefore(end) && !time.isBefore(end)) date = date.plusDays(1);
        return LocalDateTime.ofInstant(date.atTime(start).atZone(zone).toInstant(), ZoneOffset.UTC);
    }

    private boolean insideWindow(LocalTime time, LocalTime start, LocalTime end) {
        return start.equals(end) || (start.isBefore(end) ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end));
    }

    private double median(List<WateringPlanData.Point> points) {
        var sorted = points.stream().map(WateringPlanData.Point::value).sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private double medianDeviation(List<WateringPlanData.Point> points, double range) {
        List<Double> deviations = new ArrayList<>();
        for (int i = 1; i < points.size() - 1; i++) {
            double smooth = median(points.subList(i - 1, i + 2));
            deviations.add(Math.abs(points.get(i).value() - smooth));
        }
        deviations.sort(Double::compareTo);
        return deviations.isEmpty() ? Math.abs(range) : deviations.get(deviations.size() / 2);
    }

    private LocalTime parseTime(Map<String, Object> cfg, String name) {
        try { return LocalTime.parse(String.valueOf(cfg.get(name))); }
        catch (Exception ex) { throw new DomainException("bad_request", "Укажите время в формате ЧЧ:ММ: " + name); }
    }

    private double num(Map<String, Object> cfg, String name) {
        Object value = cfg.get(name);
        return value instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private void positiveInteger(Map<String, Object> cfg, String name) {
        double value = num(cfg, name);
        if (!Double.isFinite(value) || value < 1 || value != Math.rint(value) || value > Integer.MAX_VALUE) fail("Укажите целое положительное число: " + name);
    }

    private void fail(String message) { throw new DomainException("bad_request", message); }
}
