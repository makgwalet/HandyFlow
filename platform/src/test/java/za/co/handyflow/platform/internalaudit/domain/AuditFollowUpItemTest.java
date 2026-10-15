package za.co.handyflow.platform.internalaudit.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFollowUpItem;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditFollowUpItemTest {
    AuditFollowUpItem item() { return AuditFollowUpItem.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()); }
    UUID who = UUID.randomUUID();

    @Test
    void startsUnassessed() {
        var i = item();
        assertFalse(i.isAssessed());
        assertFalse(i.stillOpen());
    }

    @Test
    void assessRecordsOutcomeNoteAndPerson() {
        var i = item();
        i.assess("implemented", "  Checked the new approval step in production.  ", who, "Thandi");
        assertEquals("IMPLEMENTED", i.getOutcome());
        assertEquals("Checked the new approval step in production.", i.getNote());
        assertEquals("Thandi", i.getAssessedByName());
        assertNotNull(i.getAssessedAt());
        assertFalse(i.stillOpen());
    }

    @Test
    void shortNoteOrBadOutcomeRefused() {
        var i = item();
        assertThrows(IllegalArgumentException.class, () -> i.assess("IMPLEMENTED", "ok", who, "T"));
        assertThrows(IllegalArgumentException.class, () -> i.assess("IMPLEMENTED", null, who, "T"));
        assertThrows(IllegalArgumentException.class, () -> i.assess("DONE", "A proper long note", who, "T"));
        assertThrows(IllegalArgumentException.class, () -> i.assess(null, "A proper long note", who, "T"));
        assertFalse(i.isAssessed());
    }

    @Test
    void partialAndNotImplementedStayOpen_andCanBeRevised() {
        var i = item();
        i.assess("PARTIALLY_IMPLEMENTED", "Policy written, not rolled out.", who, "T");
        assertTrue(i.stillOpen());
        i.assess("NOT_IMPLEMENTED", "Policy withdrawn again.", who, "T");
        assertTrue(i.stillOpen());
        i.assess("IMPLEMENTED", "Rolled out and tested in October.", who, "T");
        assertFalse(i.stillOpen());
    }
}
