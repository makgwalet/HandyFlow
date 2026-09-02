package za.co.handyflow.platform.bookkeeping.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BkPeriodTest {

    @Test
    void newPeriodStartsOpen() {
        BkPeriod period = BkPeriod.create(TenantId.generate(), UUID.randomUUID(), 2026, 3);
        assertEquals("OPEN", period.getStatus());
        assertTrue(period.isOpen());
        assertNull(period.getClosedAt());
    }

    @Test
    void rejectsOutOfRangeMonth() {
        TenantId tenantId = TenantId.generate();
        UUID clientId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> BkPeriod.create(tenantId, clientId, 2026, 0));
        assertThrows(IllegalArgumentException.class, () -> BkPeriod.create(tenantId, clientId, 2026, 13));
    }

    @Test
    void closeSetsStatusAndClosedByAndClosedAt() {
        BkPeriod period = BkPeriod.create(TenantId.generate(), UUID.randomUUID(), 2026, 3);
        UUID closedBy = UUID.randomUUID();
        period.close(closedBy);

        assertEquals("CLOSED", period.getStatus());
        assertFalse(period.isOpen());
        assertEquals(closedBy, period.getClosedBy());
        assertNotNull(period.getClosedAt());
    }

    @Test
    void cannotCloseAnAlreadyClosedPeriod() {
        BkPeriod period = BkPeriod.create(TenantId.generate(), UUID.randomUUID(), 2026, 3);
        period.close(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> period.close(UUID.randomUUID()));
    }

    @Test
    void reopenClearsClosedFields() {
        BkPeriod period = BkPeriod.create(TenantId.generate(), UUID.randomUUID(), 2026, 3);
        period.close(UUID.randomUUID());
        period.reopen();

        assertEquals("OPEN", period.getStatus());
        assertTrue(period.isOpen());
        assertNull(period.getClosedBy());
        assertNull(period.getClosedAt());
    }

    @Test
    void cannotReopenAPeriodThatIsNotClosed() {
        BkPeriod period = BkPeriod.create(TenantId.generate(), UUID.randomUUID(), 2026, 3);
        assertThrows(IllegalStateException.class, period::reopen);
    }
}
