package za.co.handyflow.platform.bookkeeping.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BkJournalEntryTest {

    private BkJournalEntry newEntry() {
        return BkJournalEntry.create(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "JE-00001",
                LocalDate.now(), "Test entry", null, "MANUAL", UUID.randomUUID());
    }

    @Test
    void newEntryStartsDraftWithNoLines() {
        BkJournalEntry entry = newEntry();
        assertEquals("DRAFT", entry.getStatus());
        assertTrue(entry.getLines().isEmpty());
        assertEquals(BigDecimal.ZERO, entry.getTotalDebit());
        assertEquals(BigDecimal.ZERO, entry.getTotalCredit());
        assertTrue(entry.isBalanced()); // zero == zero
    }

    @Test
    void balancedEntryWithMatchingDebitAndCreditLines() {
        BkJournalEntry entry = newEntry();
        UUID account1 = UUID.randomUUID();
        UUID account2 = UUID.randomUUID();
        entry.addLine(BkJournalLine.debit(entry.getId(), account1, new BigDecimal("500.00"), "Bank", 0));
        entry.addLine(BkJournalLine.credit(entry.getId(), account2, new BigDecimal("500.00"), "Revenue", 1));

        assertTrue(entry.isBalanced());
        assertEquals(new BigDecimal("500.00"), entry.getTotalDebit());
        assertEquals(new BigDecimal("500.00"), entry.getTotalCredit());
    }

    @Test
    void unbalancedEntryIsNotBalanced() {
        BkJournalEntry entry = newEntry();
        entry.addLine(BkJournalLine.debit(entry.getId(), UUID.randomUUID(), new BigDecimal("500.00"), "Bank", 0));
        entry.addLine(BkJournalLine.credit(entry.getId(), UUID.randomUUID(), new BigDecimal("400.00"), "Revenue", 1));

        assertFalse(entry.isBalanced());
    }

    @Test
    void postingAnUnbalancedEntryIsRejected() {
        BkJournalEntry entry = newEntry();
        entry.addLine(BkJournalLine.debit(entry.getId(), UUID.randomUUID(), new BigDecimal("500.00"), "Bank", 0));
        entry.addLine(BkJournalLine.credit(entry.getId(), UUID.randomUUID(), new BigDecimal("100.00"), "Revenue", 1));

        assertThrows(IllegalStateException.class, entry::post);
        assertEquals("DRAFT", entry.getStatus());
    }

    @Test
    void postingABalancedDraftEntrySucceeds() {
        BkJournalEntry entry = newEntry();
        entry.addLine(BkJournalLine.debit(entry.getId(), UUID.randomUUID(), new BigDecimal("500.00"), "Bank", 0));
        entry.addLine(BkJournalLine.credit(entry.getId(), UUID.randomUUID(), new BigDecimal("500.00"), "Revenue", 1));

        entry.post();
        assertEquals("POSTED", entry.getStatus());
        assertNotNull(entry.getPostedAt());
    }

    @Test
    void onlyDraftEntriesCanBePosted() {
        BkJournalEntry entry = newEntry();
        entry.addLine(BkJournalLine.debit(entry.getId(), UUID.randomUUID(), new BigDecimal("500.00"), "Bank", 0));
        entry.addLine(BkJournalLine.credit(entry.getId(), UUID.randomUUID(), new BigDecimal("500.00"), "Revenue", 1));
        entry.post();

        assertThrows(IllegalStateException.class, entry::post);
    }
}
