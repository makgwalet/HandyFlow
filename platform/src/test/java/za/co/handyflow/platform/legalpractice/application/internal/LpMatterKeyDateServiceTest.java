package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatterKeyDate;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterKeyDateRepository;
import za.co.handyflow.platform.legalpractice.dto.LpFirmKeyDateResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** The firm-wide calendar pairs each key date with its matter and refuses ranges that are backwards or too long. */
@ExtendWith(MockitoExtension.class)
class LpMatterKeyDateServiceTest {

    @Mock LpMatterKeyDateRepository repo;
    @InjectMocks LpMatterKeyDateService service;

    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    @Test
    @DisplayName("listForFirm returns each key date with its matter's number, name and status")
    void pairsKeyDatesWithMatters() {
        UUID matterId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 30);
        LpMatterKeyDate k = LpMatterKeyDate.create(tenant, matterId, "COURT_DATE", LocalDate.of(2026, 11, 12), "Hearing", null);
        LpMatter m = mock(LpMatter.class);
        when(m.getMatterNumber()).thenReturn("M-0012");
        when(m.getMatterName()).thenReturn("ABC Holdings");
        when(m.getStatus()).thenReturn("OPEN");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{k, m});
        when(repo.findInRangeWithMatter(tenant, from, to)).thenReturn(rows);

        List<LpFirmKeyDateResponse> out = service.listForFirm(tenant, from, to);

        assertEquals(1, out.size());
        assertEquals("M-0012", out.get(0).matterNumber());
        assertEquals("ABC Holdings", out.get(0).matterName());
        assertEquals("OPEN", out.get(0).matterStatus());
        assertEquals("COURT_DATE", out.get(0).dateType());
        assertEquals(LocalDate.of(2026, 11, 12), out.get(0).dueDate());
        assertEquals(matterId, out.get(0).matterId());
    }

    @Test
    @DisplayName("listForFirm refuses a backwards range, a missing date and a range over 400 days, without touching the database")
    void refusesBadRanges() {
        LocalDate d = LocalDate.of(2026, 11, 1);
        assertThrows(IllegalArgumentException.class, () -> service.listForFirm(tenant, d, d.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.listForFirm(tenant, null, d));
        assertThrows(IllegalArgumentException.class, () -> service.listForFirm(tenant, d, d.plusDays(401)));
        verify(repo, never()).findInRangeWithMatter(any(), any(), any());
    }
}
