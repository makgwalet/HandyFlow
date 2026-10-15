package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditInvestigationLogEntry;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationChainTest {
    UUID t = UUID.randomUUID(), c = UUID.randomUUID(), who = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-01T08:00:00.123456Z");

    List<AuditInvestigationLogEntry> chain(int n) {
        List<AuditInvestigationLogEntry> l = new ArrayList<>();
        AuditInvestigationLogEntry prev = null;
        for (int i = 0; i < n; i++) {
            prev = AuditInvestigationLogEntry.next(t, c, prev, at.plusSeconds(i), who, "Thandi", i == 0 ? "CASE_OPENED" : "NOTE", "line " + i);
            l.add(prev);
        }
        return l;
    }

    static void set(Object o, String field, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(field); f.setAccessible(true); f.set(o, value);
    }

    @Test
    void anUntouchedChainVerifies() {
        var r = InvestigationChain.verify(chain(5));
        assertTrue(r.ok());
        assertEquals(5, r.entries());
        assertNull(r.brokenAtSeq());
    }

    @Test
    void anEmptyChainVerifies() { assertTrue(InvestigationChain.verify(List.of()).ok()); }

    @Test
    void firstLineLinksToGenesisAndSequenceStartsAtOne() {
        var first = chain(1).get(0);
        assertEquals(1, first.getSeq());
        assertEquals(InvestigationChain.GENESIS, first.getPrevHash());
    }

    @Test
    void editingTheTextOfAnyLineIsFoundAtThatLine() throws Exception {
        var l = chain(5);
        set(l.get(2), "detail", "something else");
        var r = InvestigationChain.verify(l);
        assertFalse(r.ok());
        assertEquals(3, r.brokenAtSeq().intValue());
    }

    @Test
    void changingWhoDidItOrWhenIsFound() throws Exception {
        var l = chain(3);
        set(l.get(1), "actorId", UUID.randomUUID());
        assertEquals(2, InvestigationChain.verify(l).brokenAtSeq().intValue());
        var l2 = chain(3);
        set(l2.get(1), "at", at.plusSeconds(999));
        assertEquals(2, InvestigationChain.verify(l2).brokenAtSeq().intValue());
    }

    @Test
    void deletingALineInTheMiddleIsFound() {
        var l = chain(5);
        l.remove(2);
        assertEquals(4, InvestigationChain.verify(l).brokenAtSeq().intValue());
    }

    @Test
    void deletingTheFirstLineIsFound() {
        var l = chain(3);
        l.remove(0);
        assertEquals(2, InvestigationChain.verify(l).brokenAtSeq().intValue());
    }

    @Test
    void rewritingALineAndItsHashStillBreaksTheNextOne() throws Exception {
        var l = chain(4);
        set(l.get(1), "detail", "forged");
        set(l.get(1), "hash", InvestigationChain.hash(2, l.get(1).getAt(), l.get(1).getActorId(), "NOTE", "forged", l.get(1).getPrevHash()));
        assertEquals(3, InvestigationChain.verify(l).brokenAtSeq().intValue());
    }

    @Test
    void nullDetailAndEmptyDetailHashTheSame_soAViewLineCanBeStoredEitherWay() {
        assertEquals(InvestigationChain.hash(1, at, who, "VIEWED", null, InvestigationChain.GENESIS), InvestigationChain.hash(1, at, who, "VIEWED", "", InvestigationChain.GENESIS));
    }
}
