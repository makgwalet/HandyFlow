package za.co.handyflow.platform.controls.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.controls.domain.model.ControlException;
import za.co.handyflow.platform.controls.domain.repository.ControlExceptionRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlExceptionSummaryTest {
    @Test
    void countsOpenUnratedSeriousOverdueAndFollowUps() {
        UUID tenant = UUID.randomUUID(), p = UUID.randomUUID(), q = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 15);
        var unrated = ControlException.raise(tenant, "scm", "X", "T", UUID.randomUUID(), "WARNING", "one");
        var high = ControlException.raise(tenant, "scm", "X", "T", UUID.randomUUID(), "WARNING", "two");
        high.rate(p, "P", "HIGH", "Large amount here");
        high.assign(p, "P", today.minusDays(1));
        var resolved = ControlException.raise(tenant, "scm", "X", "T", UUID.randomUUID(), "WARNING", "three");
        resolved.rate(p, "P", "HIGH", "Large amount here");
        resolved.respond(p, "P", "Handled by finance");
        resolved.resolve(q, "Q", "Credit note received", today.plusDays(1), today);
        var repo = mock(ControlExceptionRepository.class);
        when(repo.findAllByTenant(tenant)).thenReturn(List.of(unrated, high, resolved));
        var s = new ControlExceptionService(repo).summary(TenantId.of(tenant), today.plusDays(5));
        assertEquals(2, s.open());
        assertEquals(1, s.unrated());
        assertEquals(1, s.highOrCritical());
        assertEquals(1, s.overdue());
        assertEquals(1, s.followUpsDue());
        assertEquals(0, s.reopenedEver());
    }
}
