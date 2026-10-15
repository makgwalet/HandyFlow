package za.co.handyflow.platform.internalaudit;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReviewNote;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewNoteTest {
    private final UUID tenant = UUID.randomUUID(), eng = UUID.randomUUID(), wp = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID(), preparer = UUID.randomUUID(), other = UUID.randomUUID();

    private AuditReviewNote raised() { return AuditReviewNote.raise(tenant, eng, wp, "Sample size not explained", reviewer); }

    @Test
    void newNoteBlocksSignOff() {
        assertTrue(raised().blocksSignOff());
    }

    @Test
    void shortTextRejected() {
        assertThrows(IllegalArgumentException.class, () -> AuditReviewNote.raise(tenant, eng, wp, "no", reviewer));
        var n = raised();
        assertThrows(IllegalArgumentException.class, () -> n.respond(preparer, "ok"));
    }

    @Test
    void answererCannotClear() {
        var n = raised();
        n.respond(preparer, "Explained in section 3");
        assertThrows(IllegalStateException.class, () -> n.clear(preparer));
        n.clear(other);
        assertFalse(n.blocksSignOff());
    }

    @Test
    void cannotClearUnansweredNote() {
        assertThrows(IllegalStateException.class, () -> raised().clear(other));
    }

    @Test
    void returnedNoteReopensAndKeepsHistory() {
        var n = raised();
        n.respond(preparer, "Explained in section 3");
        n.returnWithComment(reviewer, "Section 3 does not cover this");
        assertEquals("OPEN", n.getStatus());
        assertEquals(1, n.getTimesReturned());
        assertTrue(n.getNote().contains("Returned:"));
        assertTrue(n.blocksSignOff());
        assertNull(n.getRespondedBy());
    }
}
