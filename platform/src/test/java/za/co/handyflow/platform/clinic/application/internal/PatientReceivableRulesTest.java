package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.PatientReceivableRules.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PatientReceivableRulesTest {
    private static BigDecimal r(String v) { return new BigDecimal(v); }
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    private static Open open(UUID id, String day, String owed) { return new Open(id, Instant.parse(day + "T08:00:00Z"), r(owed)); }

    @Test void owedIsThePortionLessWhatIsLinkedNeverNegative() {
        assertEquals(r("60"), PatientReceivableRules.owed(r("100"), r("40")));
        assertEquals(r("0"), PatientReceivableRules.owed(r("100"), r("150")));
        assertEquals(r("100"), PatientReceivableRules.owed(r("100"), null));
        assertEquals(r("0"), PatientReceivableRules.owed(null, null));
    }
    @Test void unlinkedIsWhatHasNotBeenLinked() {
        assertEquals(r("30"), PatientReceivableRules.unlinked(r("100"), r("70")));
        assertEquals(r("0"), PatientReceivableRules.unlinked(r("100"), r("100")));
    }
    @Test void theOldestVisitIsPaidFirst() {
        Split s = PatientReceivableRules.oldestFirst(r("150"), List.of(open(B, "2026-09-02", "100"), open(A, "2026-09-01", "100")));
        assertEquals(List.of(A, B), s.parts().stream().map(Part::claimId).toList());
        assertEquals(List.of(r("100"), r("50")), s.parts().stream().map(Part::amount).toList());
        assertEquals(r("0"), s.onAccount());
    }
    @Test void moneyBeyondWhatIsOwedStaysOnAccount() {
        Split s = PatientReceivableRules.oldestFirst(r("500"), List.of(open(A, "2026-09-01", "120.50")));
        assertEquals(r("120.50"), s.allocated());
        assertEquals(r("379.50"), s.onAccount());
    }
    @Test void visitsThatOweNothingAreSkipped() {
        Split s = PatientReceivableRules.oldestFirst(r("50"), List.of(open(A, "2026-09-01", "0"), open(B, "2026-09-02", "80")));
        assertEquals(List.of(B), s.parts().stream().map(Part::claimId).toList());
        assertEquals(r("0"), s.onAccount());
    }
    @Test void nothingOpenMeansAllOnAccount() {
        Split s = PatientReceivableRules.oldestFirst(r("75"), List.of());
        assertTrue(s.parts().isEmpty());
        assertEquals(r("75"), s.onAccount());
    }
    @Test void noPartIsEverMoreThanThePayment() {
        Split s = PatientReceivableRules.oldestFirst(r("30"), List.of(open(A, "2026-09-01", "100"), open(B, "2026-09-02", "100")));
        assertEquals(r("30"), s.allocated());
        assertEquals(1, s.parts().size());
    }
    @Test void aChosenVisitTakesUpToWhatItOwes() {
        Split s = PatientReceivableRules.toVisit(B, r("200"), List.of(open(A, "2026-09-01", "100"), open(B, "2026-09-02", "80")));
        assertEquals(List.of(new Part(B, r("80"))), s.parts());
        assertEquals(r("120"), s.onAccount());
    }
    @Test void aVisitThatIsNotOpenTakesNothing() {
        Split s = PatientReceivableRules.toVisit(C, r("40"), List.of(open(A, "2026-09-01", "100")));
        assertTrue(s.parts().isEmpty());
        assertEquals(r("40"), s.onAccount());
    }
    @Test void sameDayVisitsAreOrderedStablyById() {
        Split s = PatientReceivableRules.oldestFirst(r("10"), List.of(open(B, "2026-09-01", "100"), open(A, "2026-09-01", "100")));
        assertEquals(A, s.parts().get(0).claimId());
    }
}
