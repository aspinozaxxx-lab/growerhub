package ru.growerhub.backend.advisor.engine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import ru.growerhub.backend.advisor.jpa.AssistantUsageEntity;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;

public final class AssistantBudget {
    private AssistantBudget() {}

    public static long charge(long input, long output, AssistantSettings.Budget prices) {
        if (input < 0 || output < 0) throw new IllegalArgumentException("Invalid usage");
        long nanos = Math.addExact(Math.multiplyExact(input, prices.inputNanoUsd()),
                Math.multiplyExact(output, prices.outputNanoUsd()));
        return Math.addExact(nanos, 999) / 1000;
    }

    public static long spent(List<AssistantUsageEntity> rows, LocalDateTime since,
            Predicate<AssistantUsageEntity> scope) {
        return rows.stream().filter(u -> u.paid && scope.test(u))
                .mapToLong(u -> u.reservedMicros + (chargedInWindow(u, since) ? u.chargedMicros : 0)).sum();
    }

    public static boolean chargedInWindow(AssistantUsageEntity row, LocalDateTime since) {
        return !row.createdAt.isBefore(since) || row.finishedAt != null && !row.finishedAt.isBefore(since);
    }

    public static long requests(List<AssistantUsageEntity> rows, LocalDateTime since,
            Predicate<AssistantUsageEntity> scope) {
        return rows.stream().filter(scope).filter(u -> !u.createdAt.isBefore(since)).count();
    }

    public static boolean active(AssistantUsageEntity row) {
        return "queued".equals(row.status) || "running".equals(row.status);
    }

    public static LocalDateTime monthStart(LocalDateTime now) {
        return now.withDayOfMonth(1).toLocalDate().atStartOfDay();
    }
}
