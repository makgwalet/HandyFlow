package za.co.handyflow.platform.businessreadiness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequirementReadinessEvaluatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    static final LocalDate CLOSING = LocalDate.of(2026, 11, 15);

    private static RequirementRule reg(String authority, String type) { return new RequirementRule(authority, type, null); }
    private static RequirementRule doc(String documentType) { return new RequirementRule(null, null, documentType); }
    private static RegistrationFact r(String authority, String type, String status, LocalDate expiry) { return new RegistrationFact(authority, type, status, expiry); }
    private static DocumentFact d(String type, LocalDate expiry, boolean verified) { return new DocumentFact(type, expiry, verified); }
    private static RequirementToEvaluate req(RequirementRule rule) { return new RequirementToEvaluate(UUID.randomUUID(), "Requirement", "PENDING_REVIEW", rule, false); }
    private static RequirementToEvaluate req(RequirementRule rule, String manual) { return new RequirementToEvaluate(UUID.randomUUID(), "Requirement", manual, rule, false); }

    /** Judged as of the closing date (the normal tender case). */
    private static ReadinessItem one(RequirementToEvaluate q, List<RegistrationFact> regs, List<DocumentFact> docs) {
        return RequirementReadinessEvaluator.evaluate(TODAY, CLOSING, "CLOSING_DATE", List.of(q), regs, docs).items().get(0);
    }
    private static ReadinessItem reg1(RequirementRule rule, RegistrationFact... regs) { return one(req(rule), List.of(regs), List.of()); }
    private static ReadinessItem doc1(RequirementRule rule, DocumentFact... docs) { return one(req(rule), List.of(), List.of(docs)); }

    // ---- registrations -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an ACTIVE registration with no expiry date meets the requirement and never expires")
    void activeNoExpiry() {
        ReadinessItem i = reg1(reg("CIPC", null), r("CIPC", "Company registration", "ACTIVE", null));
        assertEquals(ReadinessResult.MET, i.result()); assertNull(i.expiresOn()); assertFalse(i.expiringSoon());
    }

    @Test
    @DisplayName("an ACTIVE registration valid on the closing date meets it, and reports when it expires")
    void activeWithExpiry() {
        ReadinessItem i = reg1(reg("CSD", null), r("CSD", "Supplier", "ACTIVE", LocalDate.of(2027, 6, 30)));
        assertEquals(ReadinessResult.MET, i.result()); assertEquals(LocalDate.of(2027, 6, 30), i.expiresOn()); assertFalse(i.expiringSoon());
        assertTrue(i.detail().contains("expires 2027-06-30"), i.detail());
    }

    @Test
    @DisplayName("a registration expiring on the closing date itself is still valid that day")
    void expiryOnClosingDayIsValid() {
        assertEquals(ReadinessResult.MET, reg1(reg("CSD", null), r("CSD", "Supplier", "ACTIVE", CLOSING)).result());
    }

    @Test
    @DisplayName("the day before the closing date is too late: it is EXPIRED for this tender even though it is still valid today, and says so")
    void expiresBeforeClosing() {
        ReadinessItem i = reg1(reg("BBBEE", null), r("BBBEE", "Certificate", "ACTIVE", CLOSING.minusDays(1)));
        assertEquals(ReadinessResult.EXPIRED, i.result());
        assertTrue(i.detail().contains("expires 2026-11-14, before the closing date 2026-11-15"), i.detail());
        assertEquals(CLOSING.minusDays(1), i.expiresOn());
    }

    @Test
    @DisplayName("a registration already past its expiry date says it expired on that date")
    void alreadyExpired() {
        ReadinessItem i = reg1(reg("SARS", null), r("SARS", "TCS", "ACTIVE", TODAY.minusDays(10)));
        assertEquals(ReadinessResult.EXPIRED, i.result()); assertTrue(i.detail().contains("expired on 2026-09-24"), i.detail());
    }

    @Test
    @DisplayName("expiring soon means valid on the closing date but within 30 days after it, inclusive of the 30th day and not the 31st")
    void expiringSoonBoundary() {
        assertTrue(reg1(reg("CSD", null), r("CSD", "S", "ACTIVE", CLOSING.plusDays(30))).expiringSoon());
        assertFalse(reg1(reg("CSD", null), r("CSD", "S", "ACTIVE", CLOSING.plusDays(31))).expiringSoon());
        assertTrue(reg1(reg("CSD", null), r("CSD", "S", "ACTIVE", CLOSING)).expiringSoon());
    }

    @Test
    @DisplayName("no matching registration is MISSING, and says which one")
    void missing() {
        ReadinessItem i = reg1(reg("CIDB", "Contractor"));
        assertEquals(ReadinessResult.MISSING, i.result()); assertTrue(i.detail().contains("No CIDB Contractor registration"), i.detail());
    }

    @Test
    @DisplayName("authority and type must both match when both are given; a blank type matches any type of that authority")
    void matchingRules() {
        List<RegistrationFact> regs = List.of(r("CIDB", "Contractor", "ACTIVE", null));
        assertEquals(ReadinessResult.MET, one(req(reg("CIDB", null)), regs, List.of()).result());
        assertEquals(ReadinessResult.MET, one(req(reg("CIDB", "Contractor")), regs, List.of()).result());
        assertEquals(ReadinessResult.MISSING, one(req(reg("CIDB", "Grading")), regs, List.of()).result());
        assertEquals(ReadinessResult.MISSING, one(req(reg("SARS", null)), regs, List.of()).result());
    }

    @Test
    @DisplayName("matching ignores case and surrounding spaces")
    void matchingIgnoresCase() {
        assertEquals(ReadinessResult.MET, reg1(reg("  csd ", " supplier "), r("CSD", "Supplier", "ACTIVE", null)).result());
    }

    @Test
    @DisplayName("a rule can name only a registration type, matching any authority")
    void typeOnlyRule() {
        assertEquals(ReadinessResult.MET, reg1(reg(null, "TCS"), r("SARS", "TCS", "ACTIVE", null)).result());
    }

    @Test
    @DisplayName("a NOT_APPLICABLE registration is ignored as if it did not exist")
    void notApplicableRegistrationIgnored() {
        assertEquals(ReadinessResult.MISSING, reg1(reg("PSIRA", null), r("PSIRA", "Reg", "NOT_APPLICABLE", null)).result());
    }

    @Test
    @DisplayName("a PENDING registration is PENDING, not MISSING and not MET")
    void pendingRegistration() {
        ReadinessItem i = reg1(reg("CIDB", null), r("CIDB", "Contractor", "PENDING", null));
        assertEquals(ReadinessResult.PENDING, i.result()); assertTrue(i.detail().contains("still pending"), i.detail());
    }

    @Test
    @DisplayName("a lapsed registration says it lapsed")
    void lapsed() {
        ReadinessItem i = reg1(reg("NHBRC", null), r("NHBRC", "Enrolment", "LAPSED", null));
        assertEquals(ReadinessResult.EXPIRED, i.result()); assertTrue(i.detail().contains("has lapsed"), i.detail());
    }

    @Test
    @DisplayName("lapsed only counts as lapsed when every match is lapsed; mixed with an expired one it is judged by the expiry")
    void lapsedMixedWithExpired() {
        ReadinessItem i = reg1(reg("NHBRC", null), r("NHBRC", "A", "LAPSED", null), r("NHBRC", "B", "EXPIRED", TODAY.minusDays(20)));
        assertEquals(ReadinessResult.EXPIRED, i.result()); assertTrue(i.detail().contains("expired on 2026-09-14"), i.detail()); assertFalse(i.detail().contains("lapsed"));
    }

    @Test
    @DisplayName("a registration marked EXPIRED with no date, or with a date that has not passed, says it is marked expired rather than inventing a reason")
    void markedExpired() {
        assertTrue(reg1(reg("X", null), r("X", "T", "EXPIRED", null)).detail().contains("marked expired"));
        ReadinessItem future = reg1(reg("X", null), r("X", "T", "EXPIRED", CLOSING.plusDays(100)));
        assertEquals(ReadinessResult.EXPIRED, future.result()); assertTrue(future.detail().contains("marked expired (expiry date 2027-02-23)"), future.detail());
    }

    @Test
    @DisplayName("with several registrations, a valid one wins over an expired one")
    void validBeatsExpired() {
        ReadinessItem i = reg1(reg("CSD", null), r("CSD", "Old", "EXPIRED", TODAY.minusDays(400)), r("CSD", "New", "ACTIVE", LocalDate.of(2027, 8, 1)));
        assertEquals(ReadinessResult.MET, i.result()); assertEquals(LocalDate.of(2027, 8, 1), i.expiresOn());
    }

    @Test
    @DisplayName("with several valid registrations the latest expiry is reported, and one with no expiry beats any dated one")
    void latestExpiryWins() {
        assertEquals(LocalDate.of(2028, 1, 1), reg1(reg("CSD", null), r("CSD", "A", "ACTIVE", LocalDate.of(2027, 1, 1)), r("CSD", "B", "ACTIVE", LocalDate.of(2028, 1, 1))).expiresOn());
        assertNull(reg1(reg("CSD", null), r("CSD", "A", "ACTIVE", LocalDate.of(2027, 1, 1)), r("CSD", "B", "ACTIVE", null)).expiresOn());
    }

    @Test
    @DisplayName("an expired and a pending registration together are PENDING (the pending one may become valid)")
    void pendingBeatsExpired() {
        assertEquals(ReadinessResult.PENDING, reg1(reg("CIDB", null), r("CIDB", "A", "EXPIRED", TODAY.minusDays(5)), r("CIDB", "B", "PENDING", null)).result());
    }

    @Test
    @DisplayName("with no closing date the judgement is as of today")
    void todayBasis() {
        ReadinessAssessment a = RequirementReadinessEvaluator.evaluate(TODAY, TODAY, "TODAY", List.of(req(reg("CSD", null))), List.of(r("CSD", "S", "ACTIVE", TODAY.minusDays(1))), List.of());
        assertEquals(ReadinessResult.EXPIRED, a.items().get(0).result()); assertEquals("TODAY", a.asOfBasis()); assertEquals(TODAY, a.asOf());
        assertTrue(a.items().get(0).detail().contains("expired on"));
    }

    // ---- documents -----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("no document of the type is MISSING")
    void documentMissing() {
        ReadinessItem i = doc1(doc("BBBEE Certificate"));
        assertEquals(ReadinessResult.MISSING, i.result()); assertTrue(i.detail().contains("No BBBEE Certificate document"), i.detail());
    }

    @Test
    @DisplayName("an unexpired but unverified document is PENDING until someone verifies it")
    void documentUnverified() {
        ReadinessItem i = doc1(doc("BBBEE Certificate"), d("BBBEE Certificate", LocalDate.of(2027, 1, 1), false));
        assertEquals(ReadinessResult.PENDING, i.result()); assertTrue(i.detail().contains("not yet verified"));
    }

    @Test
    @DisplayName("an unexpired verified document meets it")
    void documentVerified() {
        ReadinessItem i = doc1(doc("BBBEE Certificate"), d("bbbee certificate", LocalDate.of(2027, 1, 1), true));
        assertEquals(ReadinessResult.MET, i.result()); assertEquals(LocalDate.of(2027, 1, 1), i.expiresOn());
    }

    @Test
    @DisplayName("an expired document is EXPIRED, and a document expiring before the closing date says so")
    void documentExpired() {
        ReadinessItem past = doc1(doc("Insurance"), d("Insurance", TODAY.minusDays(3), true));
        assertEquals(ReadinessResult.EXPIRED, past.result()); assertTrue(past.detail().contains("expired on 2026-10-01"), past.detail());
        ReadinessItem soon = doc1(doc("Insurance"), d("Insurance", CLOSING.minusDays(2), true));
        assertEquals(ReadinessResult.EXPIRED, soon.result()); assertTrue(soon.detail().contains("before the closing date 2026-11-15"), soon.detail());
    }

    @Test
    @DisplayName("a verified document that is expired does not hide a newer unverified one: the newer one makes it PENDING")
    void newerUnverifiedBeatsOldVerified() {
        assertEquals(ReadinessResult.PENDING, doc1(doc("COIDA"), d("COIDA", TODAY.minusDays(30), true), d("COIDA", LocalDate.of(2027, 5, 1), false)).result());
    }

    @Test
    @DisplayName("a document expiring on the closing date itself is still valid that day")
    void documentExpiryOnClosingDay() {
        assertEquals(ReadinessResult.MET, doc1(doc("Insurance"), d("Insurance", CLOSING, true)).result());
    }

    @Test
    @DisplayName("with several verified documents the latest expiry is reported, and one with no expiry beats any dated one")
    void documentLatestExpiry() {
        assertEquals(LocalDate.of(2028, 1, 1), doc1(doc("COIDA"), d("COIDA", LocalDate.of(2027, 1, 1), true), d("COIDA", LocalDate.of(2028, 1, 1), true)).expiresOn());
        assertNull(doc1(doc("COIDA"), d("COIDA", LocalDate.of(2027, 1, 1), true), d("COIDA", null, true)).expiresOn());
    }

    @Test
    @DisplayName("a verified document with no expiry never expires")
    void documentNoExpiry() {
        ReadinessItem i = doc1(doc("Company Profile"), d("Company Profile", null, true));
        assertEquals(ReadinessResult.MET, i.result()); assertNull(i.expiresOn());
    }

    // ---- combined rules ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a rule needing a registration AND a document is only MET when both are; the worse result wins and both reasons are shown")
    void combinedRule() {
        RequirementRule both = new RequirementRule("CSD", null, "CSD Report");
        List<RegistrationFact> okReg = List.of(r("CSD", "S", "ACTIVE", null));
        ReadinessItem pending = one(req(both), okReg, List.of(d("CSD Report", null, false)));
        assertEquals(ReadinessResult.PENDING, pending.result()); assertTrue(pending.detail().contains("registration is valid") && pending.detail().contains("not yet verified"), pending.detail());
        assertEquals(ReadinessResult.MISSING, one(req(both), List.of(), List.of(d("CSD Report", null, true))).result());
        assertEquals(ReadinessResult.MISSING, one(req(both), okReg, List.of()).result());
        assertEquals(ReadinessResult.MET, one(req(both), okReg, List.of(d("CSD Report", null, true))).result());
    }

    @Test
    @DisplayName("combining results, MISSING is worse than EXPIRED, which is worse than PENDING, which is worse than MET")
    void severityOrder() {
        RequirementRule both = new RequirementRule("CSD", null, "CSD Report");
        RegistrationFact expiredReg = r("CSD", "S", "ACTIVE", TODAY.minusDays(1)), pendingReg = r("CSD", "S", "PENDING", null);
        DocumentFact pendingDoc = d("CSD Report", null, false), expiredDoc = d("CSD Report", TODAY.minusDays(1), true);

        assertEquals(ReadinessResult.EXPIRED, one(req(both), List.of(expiredReg), List.of(pendingDoc)).result());
        assertEquals(ReadinessResult.EXPIRED, one(req(both), List.of(pendingReg), List.of(expiredDoc)).result());
        assertEquals(ReadinessResult.MISSING, one(req(both), List.of(expiredReg), List.of()).result());
        assertEquals(ReadinessResult.MISSING, one(req(both), List.of(), List.of(expiredDoc)).result());
        assertEquals(ReadinessResult.PENDING, one(req(both), List.of(pendingReg), List.of(pendingDoc)).result());
    }

    @Test
    @DisplayName("when both parts are met, the earlier expiry is the one that binds, and expiring soon is judged on it")
    void combinedExpiry() {
        RequirementRule both = new RequirementRule("CSD", null, "CSD Report");
        ReadinessItem i = one(req(both), List.of(r("CSD", "S", "ACTIVE", LocalDate.of(2028, 1, 1))), List.of(d("CSD Report", CLOSING.plusDays(10), true)));
        assertEquals(CLOSING.plusDays(10), i.expiresOn()); assertTrue(i.expiringSoon());
    }

    // ---- not evaluated / not applicable --------------------------------------------------------------------------------

    @Test
    @DisplayName("a requirement with no rule is NOT_EVALUATED and says why")
    void noRule() {
        ReadinessItem i = one(req(null), List.of(), List.of());
        assertEquals(ReadinessResult.NOT_EVALUATED, i.result()); assertTrue(i.detail().contains("No evidence rule"), i.detail());
    }

    @Test
    @DisplayName("an empty rule is the same as no rule")
    void emptyRule() {
        assertEquals(ReadinessResult.NOT_EVALUATED, one(req(new RequirementRule(" ", null, "")), List.of(), List.of()).result());
    }

    @Test
    @DisplayName("a requirement not linked to a tracked requirement says so")
    void unlinked() {
        ReadinessItem i = one(new RequirementToEvaluate(null, "Typed by hand", "PENDING_REVIEW", null, false), List.of(), List.of());
        assertEquals(ReadinessResult.NOT_EVALUATED, i.result()); assertTrue(i.detail().contains("Not linked to a tracked requirement"), i.detail());
    }

    @Test
    @DisplayName("a requirement the user marked NOT_APPLICABLE is not evaluated, whatever the evidence says")
    void manualNotApplicable() {
        ReadinessItem i = one(req(reg("CSD", null), "NOT_APPLICABLE"), List.of(), List.of());
        assertEquals(ReadinessResult.NOT_APPLICABLE, i.result()); assertFalse(i.differsFromManualStatus());
    }

    // ---- disagreement with the user's own tick -------------------------------------------------------------------------

    @Test
    @DisplayName("ticked MET but the evidence says missing, expired or pending is flagged as differing")
    void differsWhenMetButNot() {
        assertTrue(one(req(reg("CSD", null), "MET"), List.of(), List.of()).differsFromManualStatus());
        assertTrue(one(req(reg("CSD", null), "MET"), List.of(r("CSD", "S", "ACTIVE", TODAY.minusDays(1))), List.of()).differsFromManualStatus());
        assertTrue(one(req(reg("CSD", null), "MET"), List.of(r("CSD", "S", "PENDING", null)), List.of()).differsFromManualStatus());
    }

    @Test
    @DisplayName("ticked MET and the evidence agrees is not flagged")
    void agreesWhenMet() {
        assertFalse(one(req(reg("CSD", null), "MET"), List.of(r("CSD", "S", "ACTIVE", null)), List.of()).differsFromManualStatus());
    }

    @Test
    @DisplayName("ticked MISSING but the evidence says it is met is flagged, so a stale tick is noticed")
    void differsWhenMissingButMet() {
        assertTrue(one(req(reg("CSD", null), "MISSING"), List.of(r("CSD", "S", "ACTIVE", null)), List.of()).differsFromManualStatus());
        assertFalse(one(req(reg("CSD", null), "MISSING"), List.of(), List.of()).differsFromManualStatus());
    }

    @Test
    @DisplayName("PENDING_REVIEW is never flagged, and neither is a requirement that was not evaluated")
    void neverFlagged() {
        assertFalse(one(req(reg("CSD", null), "PENDING_REVIEW"), List.of(), List.of()).differsFromManualStatus());
        assertFalse(one(req(null, "MET"), List.of(), List.of()).differsFromManualStatus());
    }

    @Test
    @DisplayName("the user's own status is carried through unchanged: the evaluator never overwrites it")
    void manualStatusUnchanged() {
        assertEquals("MET", one(req(reg("CSD", null), "MET"), List.of(), List.of()).manualStatus());
    }

    @Test
    @DisplayName("a newer requirement version being available is passed through")
    void newerVersionFlag() {
        RequirementToEvaluate q = new RequirementToEvaluate(UUID.randomUUID(), "R", "PENDING_REVIEW", reg("CSD", null), true);
        assertTrue(one(q, List.of(), List.of()).newerVersionAvailable());
    }

    // ---- summary -------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the summary counts each result, and the counts add up to the total")
    void summaryCounts() {
        List<RequirementToEvaluate> reqs = List.of(req(reg("A", null), "MET"), req(reg("B", null)), req(reg("C", null)), req(reg("D", null)), req(null), req(reg("E", null), "NOT_APPLICABLE"), req(reg("F", null)));
        List<RegistrationFact> regs = List.of(r("A", "t", "ACTIVE", null), r("C", "t", "EXPIRED", TODAY.minusDays(1)), r("D", "t", "PENDING", null), r("F", "t", "ACTIVE", CLOSING.plusDays(5)));

        ReadinessSummary s = RequirementReadinessEvaluator.evaluate(TODAY, CLOSING, "CLOSING_DATE", reqs, regs, List.of()).summary();

        assertEquals(7, s.total()); assertEquals(2, s.met()); assertEquals(1, s.missing()); assertEquals(1, s.expired()); assertEquals(1, s.pending());
        assertEquals(1, s.notEvaluated()); assertEquals(1, s.notApplicable()); assertEquals(1, s.expiringSoon()); assertEquals(0, s.differFromManualStatus());
        assertEquals(s.total(), s.met() + s.missing() + s.expired() + s.pending() + s.notEvaluated() + s.notApplicable());
    }

    @Test
    @DisplayName("the summary counts disagreements with the user's ticks")
    void summaryDiffer() {
        List<RequirementToEvaluate> reqs = List.of(req(reg("A", null), "MET"), req(reg("B", null), "MISSING"));
        ReadinessSummary s = RequirementReadinessEvaluator.evaluate(TODAY, CLOSING, "CLOSING_DATE", reqs, List.of(r("B", "t", "ACTIVE", null)), List.of()).summary();
        assertEquals(2, s.differFromManualStatus());
    }

    @Test
    @DisplayName("nothing to evaluate gives an empty assessment with zero counts, and the date and its basis are carried through")
    void emptyAssessment() {
        ReadinessAssessment a = RequirementReadinessEvaluator.evaluate(TODAY, CLOSING, "CLOSING_DATE", List.of(), List.of(), List.of());
        assertTrue(a.items().isEmpty()); assertEquals(0, a.summary().total()); assertEquals(CLOSING, a.asOf()); assertEquals("CLOSING_DATE", a.asOfBasis());
    }

    @Test
    @DisplayName("items come back in the order the requirements were given")
    void orderPreserved() {
        RequirementToEvaluate a = new RequirementToEvaluate(UUID.randomUUID(), "First", "PENDING_REVIEW", null, false), b = new RequirementToEvaluate(UUID.randomUUID(), "Second", "PENDING_REVIEW", null, false);
        List<ReadinessItem> items = RequirementReadinessEvaluator.evaluate(TODAY, CLOSING, "CLOSING_DATE", List.of(a, b), List.of(), List.of()).items();
        assertEquals(List.of("First", "Second"), items.stream().map(ReadinessItem::label).toList());
    }
}
