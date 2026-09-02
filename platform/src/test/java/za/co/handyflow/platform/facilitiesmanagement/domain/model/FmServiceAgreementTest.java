package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmServiceAgreementTest {

    @Test
    void retainerRequiresPositiveMonthlyFee() {
        TenantId tenantId = TenantId.generate();
        UUID clientId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> FmServiceAgreement.create(tenantId, clientId, "RETAINER", null, null, LocalDate.now(), null));
        assertThrows(IllegalArgumentException.class,
                () -> FmServiceAgreement.create(tenantId, clientId, "RETAINER", BigDecimal.ZERO, null, LocalDate.now(), null));
    }

    @Test
    void timeAndMaterialsDoesNotRequireMonthlyFee() {
        FmServiceAgreement agreement = FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(),
                "TIME_AND_MATERIALS", null, new BigDecimal("650.00"), LocalDate.now(), null);
        assertFalse(agreement.isRetainer());
        assertEquals("ACTIVE", agreement.getStatus());
    }

    @Test
    void endDateMustBeAfterStartDate() {
        TenantId tenantId = TenantId.generate();
        UUID clientId = UUID.randomUUID();
        LocalDate start = LocalDate.now();
        assertThrows(IllegalArgumentException.class,
                () -> FmServiceAgreement.create(tenantId, clientId, "TIME_AND_MATERIALS", null,
                        new BigDecimal("500"), start, start));
    }

    @Test
    void coversDateRespectsActiveStatusAndDateBounds() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        FmServiceAgreement agreement = FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(),
                "RETAINER", new BigDecimal("5000.00"), null, start, end);

        assertFalse(agreement.coversDate(start.minusDays(1)));
        assertTrue(agreement.coversDate(start));
        assertTrue(agreement.coversDate(LocalDate.of(2026, 6, 15)));
        assertTrue(agreement.coversDate(end));
        assertFalse(agreement.coversDate(end.plusDays(1)));

        agreement.end();
        assertFalse(agreement.coversDate(start));
        assertEquals("ENDED", agreement.getStatus());
    }

    @Test
    void coversDateWithNoEndDateIsOpenEnded() {
        LocalDate start = LocalDate.now().minusMonths(1);
        FmServiceAgreement agreement = FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(),
                "RETAINER", new BigDecimal("3000.00"), null, start, null);
        assertTrue(agreement.coversDate(LocalDate.now().plusYears(5)));
    }
}
