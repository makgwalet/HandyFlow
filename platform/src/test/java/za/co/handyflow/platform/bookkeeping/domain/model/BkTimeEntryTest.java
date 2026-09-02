package za.co.handyflow.platform.bookkeeping.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BkTimeEntryTest {

    private BkTimeEntry newEntry() {
        return BkTimeEntry.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "Jane Practitioner",
                LocalDate.now(), "BOOKKEEPING", "Monthly reconciliation", new BigDecimal("2.50"),
                new BigDecimal("650.00"), true);
    }

    @Test
    void newBillableEntryStartsUnbilled() {
        BkTimeEntry entry = newEntry();
        assertEquals("UNBILLED", entry.getStatus());
        assertTrue(entry.isEditable());
    }

    @Test
    void nonBillableEntryStartsNonBillable() {
        BkTimeEntry entry = BkTimeEntry.create(TenantId.generate(), UUID.randomUUID(), null, "Jane",
                LocalDate.now(), "ADMIN", "Internal admin", BigDecimal.ONE, new BigDecimal("650.00"), false);
        assertEquals("NON_BILLABLE", entry.getStatus());
    }

    @Test
    void lineTotalIsHoursTimesHourlyRate() {
        BkTimeEntry entry = newEntry();
        assertEquals(new BigDecimal("1625.00"), entry.lineTotal());
    }

    @Test
    void markBilledOnlyAllowedFromUnbilled() {
        BkTimeEntry entry = newEntry();
        UUID invoiceId = UUID.randomUUID();
        entry.markBilled(invoiceId);
        assertEquals("BILLED", entry.getStatus());
        assertEquals(invoiceId, entry.getInvoiceId());
        assertThrows(IllegalStateException.class, () -> entry.markBilled(UUID.randomUUID()));
    }

    @Test
    void billedEntryIsNoLongerEditable() {
        BkTimeEntry entry = newEntry();
        entry.markBilled(UUID.randomUUID());
        assertFalse(entry.isEditable());
        assertThrows(IllegalStateException.class, () -> entry.update(LocalDate.now(), "BOOKKEEPING",
                "Changed", new BigDecimal("3.00"), new BigDecimal("700.00"), true));
    }

    @Test
    void writeOffIsAllowedRegardlessOfCurrentStatus() {
        BkTimeEntry entry = newEntry();
        entry.writeOff();
        assertEquals("WRITTEN_OFF", entry.getStatus());
    }

    @Test
    void updateOnAnUneditedUnbilledEntrySucceeds() {
        BkTimeEntry entry = newEntry();
        entry.update(LocalDate.now(), "REVIEW", "Updated description", new BigDecimal("1.00"),
                new BigDecimal("650.00"), true);
        assertEquals("REVIEW", entry.getActivityType());
        assertEquals("UNBILLED", entry.getStatus());
    }
}
