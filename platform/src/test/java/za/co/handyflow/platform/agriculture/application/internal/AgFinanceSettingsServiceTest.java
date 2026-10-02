package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.domain.model.AgFinanceSettings;
import za.co.handyflow.platform.agriculture.domain.repository.AgFinanceSettingsRepository;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.FinanceSettingsResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.UpdateFinanceSettingsRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgFinanceSettingsServiceTest {

    @Mock AgFinanceSettingsRepository repository;
    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID user = UUID.randomUUID();

    private AgFinanceSettingsService service() { return new AgFinanceSettingsService(repository); }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void assertNumber(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    @Test
    @DisplayName("until saved, the defaults apply (45 hours, 0% on-cost) and reading creates nothing")
    void defaults() {
        when(repository.findForTenant(eq(TENANT))).thenReturn(Optional.empty());

        FinanceSettingsResponse r = service().get(TENANT);

        assertNumber("45", r.standardHoursPerWeek());
        assertNumber("0", r.labourOnCostPercent());
        assertFalse(r.configured());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("saved settings are what is in force")
    void savedSettings() {
        AgFinanceSettings row = AgFinanceSettings.create(TENANT, bd("40"), bd("2"), user);
        when(repository.findForTenant(eq(TENANT))).thenReturn(Optional.of(row));

        AgFinanceSettingsService.Effective e = service().effective(TENANT);

        assertNumber("40", e.hoursPerWeek());
        assertNumber("2", e.onCostPercent());
        assertTrue(e.configured());
    }

    @Test
    @DisplayName("the first save creates the row")
    void firstSaveCreates() {
        when(repository.findForTenant(eq(TENANT))).thenReturn(Optional.empty());

        FinanceSettingsResponse r = service().update(TENANT, user, new UpdateFinanceSettingsRequest(bd("42.5"), bd("2.35")));

        assertNumber("42.5", r.standardHoursPerWeek());
        assertNumber("2.35", r.labourOnCostPercent());
        assertTrue(r.configured());
        verify(repository).save(any(AgFinanceSettings.class));
    }

    @Test
    @DisplayName("a later save updates the same row")
    void laterSaveUpdates() {
        AgFinanceSettings row = AgFinanceSettings.create(TENANT, bd("45"), bd("0"), user);
        when(repository.findForTenant(eq(TENANT))).thenReturn(Optional.of(row));

        service().update(TENANT, UUID.randomUUID(), new UpdateFinanceSettingsRequest(bd("40"), bd("3")));

        assertNumber("40", row.getStandardHoursPerWeek());
        assertNumber("3", row.getLabourOnCostPercent());
        verify(repository).save(eq(row));
    }

    @Test
    @DisplayName("out-of-range settings are refused and nothing is saved")
    void refusesBadSettings() {
        when(repository.findForTenant(eq(TENANT))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service().update(TENANT, user, new UpdateFinanceSettingsRequest(bd("0"), bd("2"))));
        assertThrows(IllegalArgumentException.class, () -> service().update(TENANT, user, new UpdateFinanceSettingsRequest(bd("45"), bd("101"))));

        verify(repository, never()).save(any());
    }
}
