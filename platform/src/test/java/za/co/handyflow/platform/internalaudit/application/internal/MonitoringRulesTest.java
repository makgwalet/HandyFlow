package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.MonitoringRules.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringRulesTest {
    Params p = Params.defaults();
    UUID ann = UUID.randomUUID(), bob = UUID.randomUUID();
    // 2026-10-10 is a Saturday, 2026-10-12 a Monday (SAST)
    Instant sat = Instant.parse("2026-10-10T10:00:00Z"), mon = Instant.parse("2026-10-12T10:00:00Z");

    JournalIn j(String number, String entryDate, Instant postedAt, String desc, String ref, String type, String amount, UUID by, UUID postedBy) {
        return new JournalIn(UUID.randomUUID(), number, "POSTED", LocalDate.parse(entryDate), postedAt, desc, ref, type, null, new BigDecimal(amount), by, postedBy, null);
    }
    List<String> rules(List<Candidate> l) { return l.stream().map(Candidate::rule).sorted().toList(); }

    @Test
    void duplicateJournalsNeedSameDateAmountAndReference() {
        var a = j("J1", "2026-10-01", mon, "Rent", "INV-77", "MANUAL", "1500.00", ann, bob);
        var b = j("J2", "2026-10-01", mon, "Rent again", "inv 77", "MANUAL", "1500", ann, bob);
        var otherDay = j("J3", "2026-10-02", mon, "Rent", "INV-77", "MANUAL", "1500.00", ann, bob);
        var otherAmount = j("J4", "2026-10-01", mon, "Rent", "INV-77", "MANUAL", "1500.01", ann, bob);
        var r = MonitoringRules.duplicateJournals(List.of(a, b, otherDay, otherAmount));
        assertEquals(1, r.size());
        assertEquals(2, r.get(0).subjectIds().size());
        assertTrue(r.get(0).summary().startsWith("2 journals for R1500.00"));
    }

    @Test
    void journalsWithNothingToCompareOnAreNotCalledDuplicates() {
        var a = j("J1", "2026-10-01", mon, null, null, "MANUAL", "900", ann, bob);
        var b = j("J2", "2026-10-01", mon, " ", "", "MANUAL", "900", ann, bob);
        assertTrue(MonitoringRules.duplicateJournals(List.of(a, b)).isEmpty());
    }

    @Test
    void reversedAndReversingJournalsAreNotCounted() {
        var a = j("J1", "2026-10-01", mon, "Rent", "R1", "MANUAL", "500", ann, bob);
        var reversed = new JournalIn(UUID.randomUUID(), "J2", "REVERSED", a.entryDate(), mon, "Rent", "R1", "MANUAL", null, a.total(), ann, bob, null);
        var reversal = new JournalIn(UUID.randomUUID(), "J3", "POSTED", a.entryDate(), mon, "Rent", "R1", "MANUAL", null, a.total(), ann, bob, reversed.id());
        assertTrue(MonitoringRules.duplicateJournals(List.of(a, reversed, reversal)).isEmpty());
    }

    UUID acct = UUID.randomUUID();
    BankIn pay(String date, String amount, String ref) { return new BankIn(UUID.randomUUID(), acct, LocalDate.parse(date), "DEBIT", new BigDecimal(amount), "EFT", ref); }

    @Test
    void duplicateBankPaymentsMustBeCloseInTime() {
        var r = MonitoringRules.duplicateBankPayments(List.of(pay("2026-10-01", "8200.00", "ACME INV 5"), pay("2026-10-03", "8200", "acme inv 5"), pay("2026-10-20", "8200", "ACME INV 5")), 3);
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).severity());
        assertEquals(2, r.get(0).subjectIds().size());
    }

    @Test
    void creditsAndDifferentAccountsAndAmountsAreNotDuplicates() {
        var credit = new BankIn(UUID.randomUUID(), acct, LocalDate.parse("2026-10-01"), "CREDIT", new BigDecimal("100"), "x", "REF1");
        var credit2 = new BankIn(UUID.randomUUID(), acct, LocalDate.parse("2026-10-01"), "CREDIT", new BigDecimal("100"), "x", "REF1");
        var elsewhere = new BankIn(UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-01"), "DEBIT", new BigDecimal("100"), "x", "REF1");
        var here = new BankIn(UUID.randomUUID(), acct, LocalDate.parse("2026-10-01"), "DEBIT", new BigDecimal("100"), "x", "REF1");
        var other = new BankIn(UUID.randomUUID(), acct, LocalDate.parse("2026-10-01"), "DEBIT", new BigDecimal("101"), "x", "REF1");
        assertTrue(MonitoringRules.duplicateBankPayments(List.of(credit, credit2, elsewhere, here, other), 3).isEmpty());
    }

    @Test
    void threePaymentsInARowAreOneAlert() {
        var r = MonitoringRules.duplicateBankPayments(List.of(pay("2026-10-01", "50", "R"), pay("2026-10-02", "50", "R"), pay("2026-10-03", "50", "R")), 3);
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).subjectIds().size());
    }

    @Test
    void roundLargeManualJournalsAreFlagged_smallOrSystemOnesAreNot() {
        var big = j("J1", "2026-10-12", mon, "Adj", "A1", "MANUAL", "120000.00", ann, bob);
        var odd = j("J2", "2026-10-12", mon, "Adj", "A2", "MANUAL", "120000.50", ann, bob);
        var small = j("J3", "2026-10-12", mon, "Adj", "A3", "MANUAL", "2000.00", ann, bob);
        var system = new JournalIn(UUID.randomUUID(), "J4", "POSTED", LocalDate.parse("2026-10-12"), mon, "Invoice", "I1", "INVOICE", "invoice:1", new BigDecimal("120000"), null, null, null);
        assertEquals(List.of("ROUND_MANUAL_JOURNAL"), rules(MonitoringRules.manualJournalPatterns(p, List.of(big, odd, small, system))));
    }

    @Test
    void weekendPostingOfALargeManualJournal() {
        var r = MonitoringRules.manualJournalPatterns(p, List.of(j("J1", "2026-10-10", sat, "Adj", "A1", "MANUAL", "60000.25", ann, bob)));
        assertEquals(List.of("WEEKEND_POSTING"), rules(r));
        assertTrue(r.get(0).summary().contains("Saturday"));
        assertTrue(MonitoringRules.manualJournalPatterns(p, List.of(j("J2", "2026-10-12", mon, "Adj", "A2", "MANUAL", "60000.25", ann, bob))).isEmpty());
    }

    @Test
    void backdatingBeyondTheLimit_andSeverityRisesWithSize() {
        var small = j("J1", "2026-08-01", mon, "Adj", "A1", "MANUAL", "400.00", ann, bob);
        var big = j("J2", "2026-08-01", mon, "Adj", "A2", "MANUAL", "70000.10", ann, bob);
        var within = j("J3", "2026-10-01", mon, "Adj", "A3", "MANUAL", "400.00", ann, bob);
        var r = MonitoringRules.manualJournalPatterns(p, List.of(small, big, within));
        assertEquals(2, r.size());
        assertEquals("MEDIUM", r.stream().filter(c -> c.subjectIds().contains(small.id())).findFirst().get().severity());
        assertEquals("HIGH", r.stream().filter(c -> c.subjectIds().contains(big.id())).findFirst().get().severity());
    }

    @Test
    void largeJournalWithNoExplanation() {
        var r = MonitoringRules.manualJournalPatterns(p, List.of(j("J1", "2026-10-12", mon, " ", null, "MANUAL", "80000.10", ann, bob)));
        assertEquals(List.of("MISSING_EXPLANATION"), rules(r));
    }

    @Test
    void sameMakerAndPosterIsHighEvenForSystemTypeJournals() {
        var r = MonitoringRules.manualJournalPatterns(p, List.of(j("J1", "2026-10-12", mon, "Adj", "A1", "MANUAL", "10.00", ann, ann)));
        assertEquals(List.of("SAME_MAKER_AND_POSTER"), rules(r));
        assertEquals("HIGH", r.get(0).severity());
        assertTrue(r.get(0).involved().contains(ann));
    }

    @Test
    void conflictingPermissionsOnActiveUsersOnly() {
        var both = new UserIn(ann, "Ann Lead", true, Set.of("AP_APPROVE", "AP_PAY", "READ"));
        var inactive = new UserIn(bob, "Bob Gone", false, Set.of("AP_APPROVE", "AP_PAY"));
        var fine = new UserIn(UUID.randomUUID(), "Cy Fine", true, Set.of("AP_APPROVE", "INVOICE_CREATE"));
        var r = MonitoringRules.segregationConflicts(List.of(both, inactive, fine));
        assertEquals(1, r.size());
        assertEquals("HIGH", r.get(0).severity());
        assertEquals("SOD_CONFLICT:" + ann + ":AP_APPROVE_PAY", r.get(0).fingerprint());
        assertEquals("Ann Lead can approve and pay supplier bills", r.get(0).summary());
    }

    @Test
    void anEverythingRoleRaisesEveryPair() {
        var all = new HashSet<String>();
        MonitoringRules.CONFLICTS.forEach(c -> { all.add(c.a()); all.add(c.b()); });
        assertEquals(MonitoringRules.CONFLICTS.size(), MonitoringRules.segregationConflicts(List.of(new UserIn(ann, "Admin", true, all))).size());
    }

    @Test
    void fingerprintsAreStableAcrossRuns_soNothingIsFiledTwice() {
        var a = j("J1", "2026-10-01", mon, "Rent", "INV-77", "MANUAL", "1500.00", ann, bob);
        var b = j("J2", "2026-10-01", mon, "Rent", "INV-77", "MANUAL", "1500.00", ann, bob);
        var first = MonitoringRules.run(p, List.of(a, b), List.of(), List.of()).get(0).fingerprint();
        var second = MonitoringRules.run(p, List.of(b, a), List.of(), List.of()).get(0).fingerprint();
        assertEquals(first, second);
    }
}
