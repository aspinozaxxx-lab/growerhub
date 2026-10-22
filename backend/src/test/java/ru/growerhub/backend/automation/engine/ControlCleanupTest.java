package ru.growerhub.backend.automation.engine;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.automation.jpa.*;
import ru.growerhub.backend.common.config.automation.ControlSettings;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.contract.AuthUser;

class ControlCleanupTest {
    @Test void baselineRefreshedWhileCleanupWaitsForOwnerIsNotDeleted() {
        var demands = mock(ControlDemandRepository.class); var channels = mock(ControlChannelRepository.class);
        var users = mock(UserFacade.class); var row = new ControlDemandEntity(); row.id = UUID.randomUUID(); row.userId = 1;
        row.status = "released"; row.requestedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        var candidate = mock(ControlDemandRepository.CleanupCandidate.class);
        when(candidate.getId()).thenReturn(row.id); when(candidate.getUserId()).thenReturn(1);
        when(demands.cleanupCandidates(any(), any(), any())).thenReturn(List.of(candidate));
        when(users.getAuthUser(1)).thenReturn(new AuthUser(1, "user", true));
        doAnswer(call -> { row.status = "pending"; row.requestedAt = LocalDateTime.of(2026, 10, 22, 9, 0); return null; })
                .when(users).lockAccount(1);
        when(demands.findById(row.id)).thenReturn(Optional.of(row));
        var settings = new ControlSettings(1000, 30, 120, 120, 10, 20, 200, 1, 100, 1, 86400, 900, 16, 90, 90, 200, 3600000);
        new ControlService(demands, channels, null, null, null, null, null, settings, users, null, null, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneOffset.UTC)).maintain();
        verify(demands, never()).delete(any(ControlDemandEntity.class));
        verify(channels, never()).save(any());
        var order = inOrder(users, demands); order.verify(users).lockAccount(1); order.verify(demands).findById(row.id);
    }
}
