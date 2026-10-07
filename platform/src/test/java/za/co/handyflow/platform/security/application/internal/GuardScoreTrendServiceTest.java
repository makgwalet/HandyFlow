package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.GuardScoreHistoryStore.Snapshot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The score trend: window limits and the recommendation count. */
class GuardScoreTrendServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private final GuardService guardService = mock(GuardService.class);
    private final GuardScoreHistoryStore store = mock(GuardScoreHistoryStore.class);
    private final GuardScoreTrendService service = new GuardScoreTrendService(guardService, store);
    private final UUID guardId = UUID.randomUUID();

    @Test @DisplayName("Returns the snapshots with a count of recommendations, after proving the guard belongs to the tenant")
    void maps() {
        when(store.history(TENANT, guardId, 90)).thenReturn(List.of(
                new Snapshot(LocalDate.of(2026, 10, 5), 72, "GOOD", 90, ""),
                new Snapshot(LocalDate.of(2026, 10, 6), null, null, 20, "A:WARN,B:INFO")));
        var out = service.history(TENANT, guardId, null);
        verify(guardService).getGuard(TENANT, guardId);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).recommendations()).isZero();
        assertThat(out.get(1).score()).isNull();
        assertThat(out.get(1).recommendations()).isEqualTo(2);
    }

    @Test @DisplayName("The window is limited to between 1 and 365 days")
    void limits() {
        service.history(TENANT, guardId, 5000);
        verify(store).history(TENANT, guardId, 365);
        service.history(TENANT, guardId, 0);
        verify(store).history(TENANT, guardId, 1);
    }
}
