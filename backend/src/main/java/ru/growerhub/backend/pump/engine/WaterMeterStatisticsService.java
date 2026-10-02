package ru.growerhub.backend.pump.engine;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.pump.WaterMeterSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.pump.contract.WaterMeterStatistics;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeWaterMeterData;

@Service
public class WaterMeterStatisticsService {
    private final ZigbeeFacade zigbee;
    private final UserFacade users;
    private final WaterMeterSettings settings;
    private final Clock clock;

    public WaterMeterStatisticsService(@Lazy ZigbeeFacade zigbee, @Lazy UserFacade users,
                                      WaterMeterSettings settings, Clock clock) {
        this.zigbee = zigbee;
        this.users = users;
        this.settings = settings;
        this.clock = clock;
    }

    public WaterMeterStatistics statistics(AuthenticatedUser user, UUID coordinatorId, String ieee, String monthValue) {
        ZoneId zone = ZoneId.of(users.getTimezone(user.id()));
        var instant = clock.instant();
        LocalDate today = instant.atZone(zone).toLocalDate();
        YearMonth current = YearMonth.from(today);
        YearMonth month;
        try { month = monthValue == null || monthValue.isBlank() ? current : YearMonth.parse(monthValue); }
        catch (java.time.DateTimeException ex) { throw new DomainException("bad_request", "Некорректный месяц"); }
        if (month.isAfter(current) || month.isBefore(current.minusMonths(settings.availableMonths() - 1L))) {
            throw new DomainException("bad_request", "Месяц выходит за доступный диапазон истории");
        }
        LocalDateTime from = month.atDay(1).atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        LocalDateTime receivedUntil = end.plusHours(settings.lateReportHours());
        if (receivedUntil.isAfter(now)) receivedUntil = now;
        var history = zigbee.waterMeterHistory(user, coordinatorId, ieee, from, receivedUntil, settings.maxHistoryEvents());
        return summarize(history, month, zone, from, end, today);
    }

    static WaterMeterStatistics summarize(ZigbeeWaterMeterData.History history, YearMonth month, ZoneId zone,
                                         LocalDateTime from, LocalDateTime until, LocalDate today) {
        var reports = new ArrayList<>(history.observations());
        if (history.latest() != null && !history.latest().receivedAt().isBefore(from)) reports.add(history.latest());
        reports.sort(Comparator.comparing(ZigbeeWaterMeterData.Observation::receivedAt));
        var firstSeen = new LinkedHashMap<LocalDateTime, LocalDateTime>();
        for (var report : reports) if (report.startedAt() != null) firstSeen.putIfAbsent(report.startedAt(), report.receivedAt());
        var operations = new LinkedHashMap<LocalDateTime, WaterMeterStatistics.Operation>();
        for (var report : reports) {
            if (!report.complete() || report.finishedAt().isBefore(from) || !report.finishedAt().isBefore(until)) continue;
            var previous = operations.get(report.startedAt());
            boolean conflict = previous != null && (previous.issue() != null
                    || !Objects.equals(previous.volumeL(), report.volumeL())
                    || !Objects.equals(previous.finishedAt(), report.finishedAt()));
            operations.put(report.startedAt(), new WaterMeterStatistics.Operation(report.startedAt(), report.finishedAt(),
                    firstSeen.get(report.startedAt()), conflict ? null : report.volumeL(),
                    firstSeen.get(report.startedAt()).isAfter(report.finishedAt()),
                    conflict ? "inconsistent_operation_reports" : null));
        }
        List<WaterMeterStatistics.Day> days = new ArrayList<>();
        Double total = null;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            var found = operations.values().stream().filter(op ->
                    op.finishedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate().equals(date)).toList();
            Double volume = found.stream().anyMatch(op -> op.volumeL() != null)
                    ? found.stream().filter(op -> op.volumeL() != null).mapToDouble(WaterMeterStatistics.Operation::volumeL).sum() : null;
            if (volume != null) total = (total == null ? 0 : total) + volume;
            // Dostavka poslednej operacii ne dokazyvaet polnyj ohvat sutok.
            days.add(new WaterMeterStatistics.Day(date, volume, found.size(), true));
        }
        var latest = history.latest();
        return new WaterMeterStatistics(history.supported(), history.simulated(), history.label(), month.toString(), zone.getId(),
                history.simulated() ? "simulated_meter_reports" : "meter_reports", today, total, true, history.truncated(),
                latest == null ? null : latest.flowLMin(), latest == null ? null : latest.receivedAt(),
                latest == null ? null : latest.reportedDailyL(), false, "operation_end_date",
                List.copyOf(days), operations.values().stream().sorted(Comparator.comparing(
                        WaterMeterStatistics.Operation::finishedAt).reversed()).toList());
    }
}
