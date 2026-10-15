package za.co.handyflow.platform.internalaudit.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigation;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationEvidence;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationMember;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditInvestigationTest {
    UUID t = UUID.randomUUID(), c = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();

    AuditInvestigation open() { return AuditInvestigation.open(t, "INV-0001", " Till shortages ", "  ", a); }
    AuditInvestigationMember m(UUID u, String role) { return AuditInvestigationMember.of(t, c, u, "N", role, a); }

    @Test
    void numbersAndTitles() {
        assertEquals("INV-0007", AuditInvestigation.numberFor(7));
        var i = open();
        assertEquals("Till shortages", i.getTitle());
        assertNull(i.getSummary());
        assertEquals("OPEN", i.getStatus());
        assertThrows(IllegalArgumentException.class, () -> AuditInvestigation.open(t, "INV-0002", " ", null, a));
    }

    @Test
    void reviewThenCloseThenNothingMoreCanChange() {
        var i = open();
        i.setReview(true);
        assertEquals("UNDER_REVIEW", i.getStatus());
        i.setReview(false);
        assertEquals("OPEN", i.getStatus());
        assertThrows(IllegalArgumentException.class, () -> i.close("short", a));
        i.close("Concluded; referred to HR.", a);
        assertTrue(i.isClosed());
        assertEquals("Concluded; referred to HR.", i.getCloseReason());
        assertThrows(IllegalStateException.class, () -> i.close("Concluded; referred to HR.", a));
        assertThrows(IllegalStateException.class, () -> i.setReview(true));
        assertThrows(IllegalStateException.class, i::requireWritable);
    }

    @Test
    void rolesAndWriteRights() {
        assertTrue(m(a, "lead").isLead());
        assertTrue(m(a, "INVESTIGATOR").canWrite());
        assertFalse(m(a, "OBSERVER").canWrite());
        assertThrows(IllegalArgumentException.class, () -> m(a, "BOSS"));
        assertThrows(IllegalArgumentException.class, () -> m(a, null));
    }

    @Test
    void theLastLeadCannotBeRemoved_butOneOfTwoCan() {
        var l1 = m(a, "LEAD"); var l2 = m(b, "LEAD"); var inv = m(d, "INVESTIGATOR");
        assertThrows(IllegalStateException.class, () -> m(a, "LEAD").remove(List.of(m(a, "LEAD"), inv)));
        l1.remove(List.of(l1, l2, inv));
        assertFalse(l1.isActive());
        assertFalse(l1.canWrite());
        // l1 is gone, so l2 is now the last lead
        assertThrows(IllegalStateException.class, () -> l2.remove(List.of(l1, l2, inv)));
        inv.remove(List.of(l2, inv));
        assertFalse(inv.isActive());
    }

    @Test
    void removingSomeoneAlreadyRemovedIsRefused() {
        var o = m(a, "OBSERVER");
        o.remove(List.of(o));
        assertThrows(IllegalStateException.class, () -> o.remove(List.of(o)));
    }

    @Test
    void evidenceRegisterAndTransfers() {
        assertEquals("EV-012", AuditInvestigationEvidence.refFor(12));
        assertThrows(IllegalArgumentException.class, () -> AuditInvestigationEvidence.collect(t, c, "EV-001", "short", null, a, "A"));
        var e = AuditInvestigationEvidence.collect(t, c, "EV-001", "Till roll for 3 Oct", "  ", a, "Ann");
        assertEquals(a, e.getHolderId());
        assertNull(e.getSource());
        // not the holder and not a lead
        assertThrows(IllegalStateException.class, () -> e.transfer(b, false, d, "Dee"));
        // handing it to oneself
        assertThrows(IllegalArgumentException.class, () -> e.transfer(a, false, a, "Ann"));
        e.transfer(a, false, b, "Bob");
        assertEquals(b, e.getHolderId());
        // Ann no longer holds it, so she cannot move it again, but a lead can
        assertThrows(IllegalStateException.class, () -> e.transfer(a, false, d, "Dee"));
        e.transfer(a, true, d, "Dee");
        assertEquals("Dee", e.getHolderName());
    }
}
