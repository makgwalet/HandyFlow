package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.model.ShiftStatus;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Not run in the authoring sandbox (no Maven jars). */
class ShiftMissedSweeperWebhookTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private final ShiftRepository repo = mock(ShiftRepository.class);
    private final PublicApiService hooks = mock(PublicApiService.class);
    private final ShiftMissedSweeper sweeper = new ShiftMissedSweeper(repo);

    ShiftMissedSweeperWebhookTest() { ReflectionTestUtils.setField(sweeper, "webhooks", hooks); }

    @Test void sendsOneEventPerShiftThisRunMarked() {
        Shift a = mock(Shift.class), b = mock(Shift.class);
        TenantId t = mock(TenantId.class);
        UUID ia = UUID.randomUUID(), ib = UUID.randomUUID();
        when(a.getId()).thenReturn(ia); when(b.getId()).thenReturn(ib);
        when(a.getTenantId()).thenReturn(t); when(b.getTenantId()).thenReturn(t);
        when(repo.findEndedScheduled(eq(ShiftStatus.SCHEDULED), any(), any())).thenReturn(List.of(a, b));
        when(repo.markMissedIfScheduled(eq(ia), any(), any(), any())).thenReturn(1);
        when(repo.markMissedIfScheduled(eq(ib), any(), any(), any())).thenReturn(0); // another instance got it
        assertEquals(1, sweeper.sweepAt(NOW));
        verify(hooks, times(1)).dispatchWebhookEvent(eq(t), eq("SHIFT_MISSED"), eq(ia), anyString());
        verify(hooks, never()).dispatchWebhookEvent(any(), any(), eq(ib), any());
    }
}
