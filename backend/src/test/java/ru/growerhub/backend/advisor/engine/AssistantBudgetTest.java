package ru.growerhub.backend.advisor.engine;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.advisor.jpa.AssistantUsageEntity;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;
class AssistantBudgetTest {
    @Test void monthAndRollingWindowsIncludeOlderReservationsAndCrossBoundaryCompletions() {
        var month = LocalDateTime.of(2026, 11, 1, 0, 0);
        var reserved = new AssistantUsageEntity(); reserved.paid = true; reserved.createdAt = month.minusDays(40); reserved.reservedMicros = 15000;
        var completed = new AssistantUsageEntity(); completed.paid = true; completed.createdAt = month.minusSeconds(2); completed.finishedAt = month.plusSeconds(2); completed.chargedMicros = 500;
        var expired = new AssistantUsageEntity(); expired.paid = true; expired.createdAt = month.minusDays(2); expired.finishedAt = month.minusSeconds(1); expired.chargedMicros = 9999;
        assertThat(AssistantBudget.spent(List.of(reserved, completed, expired), month, u -> true)).isEqualTo(15500);
        assertThat(AssistantBudget.monthStart(month.plusDays(4))).isEqualTo(month);
        var price = new AssistantSettings.Budget(2000000, 150000, 30000, 15000, 30000, 20000, 300, 1200);
        assertThat(AssistantBudget.charge(16000, 4096, price)).isEqualTo(9716);
        assertThat(AssistantBudget.charge(16000 * 3, 4096 * 3, price)).isGreaterThan(price.turnMicros());
        assertThatThrownBy(() -> AssistantBudget.charge(Long.MAX_VALUE, 0, price)).isInstanceOf(ArithmeticException.class);
    }
}
