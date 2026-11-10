package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhsePutawayRules.Candidate;
import za.co.handyflow.platform.warehousing.application.internal.WhsePutawayRules.Suggestion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhsePutawayRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private static Candidate c(String code, String type, String cap, String onHand, Integer seq, boolean same, boolean otherClient) {
        return new Candidate(UUID.randomUUID(), code, type, true, cap == null ? null : bd(cap), bd(onHand), seq, same, otherClient);
    }

    @Test @DisplayName("a location already holding the item comes first, then an empty one, then one with other items")
    void ranking() {
        List<Suggestion> s = WhsePutawayRules.rank(List.of(
                c("MIXED", "BIN", null, "5", 1, false, false),
                c("EMPTY", "BIN", null, "0", 2, false, false),
                c("SAME", "BIN", null, "5", 9, true, false)), bd("3"), 5);
        assertEquals(List.of("SAME", "EMPTY", "MIXED"), s.stream().map(Suggestion::code).toList());
        assertEquals("Already holds this item", s.get(0).reason());
        assertEquals("Empty", s.get(1).reason());
    }

    @Test @DisplayName("ties go to the lowest picking sequence, then the code; no sequence comes last")
    void ties() {
        List<Suggestion> s = WhsePutawayRules.rank(List.of(
                c("C", "BIN", null, "0", null, false, false), c("B", "BIN", null, "0", 4, false, false), c("A", "BIN", null, "0", 4, false, false),
                c("Z", "BIN", null, "0", 1, false, false)), bd("1"), 5);
        assertEquals(List.of("Z", "A", "B", "C"), s.stream().map(Suggestion::code).toList());
    }

    @Test @DisplayName("docks, staging areas and inactive locations are never suggested")
    void notStorage() {
        Candidate inactive = new Candidate(UUID.randomUUID(), "OFF", "BIN", false, null, bd("0"), 1, false, false);
        assertTrue(WhsePutawayRules.rank(List.of(c("D", "DOCK", null, "0", 1, false, false), c("S", "STAGING", null, "0", 1, false, false), inactive), bd("1"), 5).isEmpty());
        assertTrue(WhsePutawayRules.problem(inactive, bd("1")).contains("not active"));
        assertTrue(WhsePutawayRules.problem(c("D", "DOCK", null, "0", 1, false, false), bd("1")).contains("not storage"));
    }

    @Test @DisplayName("a location holding another client's stock is never suggested")
    void noClientMixing() {
        Candidate other = c("OTHER", "BIN", null, "2", 1, false, true);
        assertTrue(WhsePutawayRules.rank(List.of(other), bd("1"), 5).isEmpty());
        assertTrue(WhsePutawayRules.problem(other, bd("1")).contains("another client"));
    }

    @Test @DisplayName("capacity must have room; the room left is reported")
    void capacity() {
        Candidate tight = c("TIGHT", "BIN", "10", "8", 1, true, false);
        Candidate roomy = c("ROOMY", "BIN", "10", "2", 2, true, false);
        List<Suggestion> s = WhsePutawayRules.rank(List.of(tight, roomy), bd("5"), 5);
        assertEquals(List.of("ROOMY"), s.stream().map(Suggestion::code).toList());
        assertEquals(0, bd("8").compareTo(s.get(0).roomLeft()));
        assertEquals("TIGHT has room for 2, not 5.", WhsePutawayRules.problem(tight, bd("5")));
        assertNull(WhsePutawayRules.problem(tight, bd("2")));
        assertNull(WhsePutawayRules.rank(List.of(c("NOCAP", "BIN", null, "0", 1, false, false)), bd("999"), 1).get(0).roomLeft());
    }

    @Test @DisplayName("the list is limited")
    void limit() {
        List<Candidate> many = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) many.add(c("L" + i, "BIN", null, "0", i, false, false));
        assertEquals(3, WhsePutawayRules.rank(many, bd("1"), 3).size());
    }
}
