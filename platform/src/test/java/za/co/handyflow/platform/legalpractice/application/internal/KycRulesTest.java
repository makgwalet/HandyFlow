package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The due diligence rules with no database: ratings, what is missing for each kind of client, and where a client stands. */
class KycRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final LocalDate DONE = LocalDate.of(2026, 11, 1);

    private static KycRules.Assessment lowRisk(String kind) {
        return new KycRules.Assessment(kind, "LOW", "LOW", "LOW", "LOW", "NONE", DONE, DONE, "CLEAR", "Residential conveyancing for a salaried client", null, null);
    }

    private static List<KycRules.Item> items(String... kinds) { return java.util.Arrays.stream(kinds).map(k -> new KycRules.Item(k, null)).toList(); }
    private static KycRules.Party party(String role, String name, String pct, String method, boolean verified) {
        return new KycRules.Party(role, name, pct == null ? null : new BigDecimal(pct), method, verified);
    }

    // ---- rating ------------------------------------------------------------------------------------------------------

    @Test
    void theOverallRiskIsTheHighestOfTheFourIndicators() {
        assertEquals("LOW", KycRules.riskOf("LOW", "LOW", "LOW", "LOW", "NONE"));
        assertEquals("MEDIUM", KycRules.riskOf("LOW", "MEDIUM", "LOW", "LOW", "NONE"));
        assertEquals("HIGH", KycRules.riskOf("LOW", "MEDIUM", "LOW", "HIGH", "NONE"));
    }

    @Test
    void noRatingUntilAllFourIndicatorsAreSet() {
        assertNull(KycRules.riskOf("LOW", "LOW", null, "LOW", "NONE"));
    }

    @Test
    void aPoliticallyExposedPersonIsAlwaysHighRisk() {
        for (String pep : List.of("DPEP", "PIP", "FPEP")) assertEquals("HIGH", KycRules.riskOf("LOW", "LOW", "LOW", "LOW", pep));
    }

    @Test
    void ratingSetsTheLevelAndTheReviewPeriod() {
        assertEquals("SIMPLIFIED", KycRules.levelOf("LOW"));
        assertEquals("STANDARD", KycRules.levelOf("MEDIUM"));
        assertEquals("ENHANCED", KycRules.levelOf("HIGH"));
        assertEquals(36, KycRules.reviewMonths("LOW"));
        assertEquals(24, KycRules.reviewMonths("MEDIUM"));
        assertEquals(12, KycRules.reviewMonths("HIGH"));
    }

    @Test
    void seniorApprovalIsNeededForHighRiskAndForAnyPoliticallyExposedPerson() {
        assertTrue(KycRules.needsSenior("HIGH", "NONE"));
        assertTrue(KycRules.needsSenior("LOW", "PIP"));
        assertFalse(KycRules.needsSenior("MEDIUM", "NONE"));
    }

    // ---- what is missing ---------------------------------------------------------------------------------------------

    @Test
    void aCompleteIndividualHasNothingMissing() {
        assertEquals(List.of(), KycRules.missing(lowRisk("INDIVIDUAL"), List.of(), items("IDENTITY", "ADDRESS")));
    }

    @Test
    void anIndividualNeedsIdentityAndResidentialAddress() {
        assertEquals(2, KycRules.missing(lowRisk("INDIVIDUAL"), List.of(), items()).size());
        assertEquals(1, KycRules.missing(lowRisk("INDIVIDUAL"), List.of(), items("IDENTITY")).size());
    }

    @Test
    void nothingCanBeSubmittedWithoutTheRiskPepAndSanctionsChecks() {
        var blank = new KycRules.Assessment("INDIVIDUAL", null, null, null, null, null, null, null, null, null, null, null);
        List<String> m = KycRules.missing(blank, List.of(), items("IDENTITY", "ADDRESS"));
        assertEquals(4, m.size());
        assertTrue(m.stream().anyMatch(s -> s.contains("four risk indicators")));
        assertTrue(m.stream().anyMatch(s -> s.contains("politically exposed")));
        assertTrue(m.stream().anyMatch(s -> s.contains("sanctions")));
        assertTrue(m.stream().anyMatch(s -> s.contains("business")));
    }

    @Test
    void aSanctionsMatchBlocksSubmission() {
        var a = new KycRules.Assessment("INDIVIDUAL", "LOW", "LOW", "LOW", "LOW", "NONE", DONE, DONE, "MATCH", "Residential conveyancing for a client", null, null);
        assertTrue(KycRules.missing(a, List.of(), items("IDENTITY", "ADDRESS")).get(0).contains("sanctions match"));
    }

    @Test
    void aShortBusinessNoteDoesNotCount() {
        var a = new KycRules.Assessment("INDIVIDUAL", "LOW", "LOW", "LOW", "LOW", "NONE", DONE, DONE, "CLEAR", "salary", null, null);
        assertEquals(1, KycRules.missing(a, List.of(), items("IDENTITY", "ADDRESS")).size());
    }

    @Test
    void highRiskNeedsSourceOfFundsAndWealth() {
        var a = new KycRules.Assessment("INDIVIDUAL", "HIGH", "LOW", "LOW", "LOW", "NONE", DONE, DONE, "CLEAR", "Cross-border property purchase", null, null);
        List<String> m = KycRules.missing(a, List.of(), items("IDENTITY", "ADDRESS"));
        assertEquals(2, m.size());
        var ok = new KycRules.Assessment("INDIVIDUAL", "HIGH", "LOW", "LOW", "LOW", "NONE", DONE, DONE, "CLEAR", "Cross-border property purchase", "Sale of a business in 2024", "Salary and dividends over 20 years");
        assertEquals(List.of(), KycRules.missing(ok, List.of(), items("IDENTITY", "ADDRESS")));
    }

    @Test
    void aCompanyNeedsRegistrationStructureAuthorisedPersonAndBeneficialOwners() {
        List<String> m = KycRules.missing(lowRisk("COMPANY"), List.of(), items());
        assertEquals(5, m.size());
        var parties = List.of(party("AUTHORISED_PERSON", "A. Director", null, null, true), party("BENEFICIAL_OWNER", "B. Owner", "60", "OWNERSHIP", true));
        assertEquals(List.of(), KycRules.missing(lowRisk("COMPANY"), parties, items("ADDRESS", "REGISTRATION", "OWNERSHIP_STRUCTURE")));
    }

    @Test
    void whenNobodyHoldsFivePercentTheWayOwnersWereFoundMustBeSaid() {
        var parties = List.of(party("AUTHORISED_PERSON", "A. Director", null, null, true), party("BENEFICIAL_OWNER", "B. Owner", "3", "OWNERSHIP", true));
        List<String> m = KycRules.missing(lowRisk("COMPANY"), parties, items("ADDRESS", "REGISTRATION", "OWNERSHIP_STRUCTURE"));
        assertEquals(1, m.size());
        assertTrue(m.get(0).contains("5%"));
        var byControl = List.of(party("AUTHORISED_PERSON", "A. Director", null, null, true), party("BENEFICIAL_OWNER", "B. Owner", "3", "OWNERSHIP", true),
                party("BENEFICIAL_OWNER", "C. Controller", null, "CONTROL", true));
        assertEquals(List.of(), KycRules.missing(lowRisk("COMPANY"), byControl, items("ADDRESS", "REGISTRATION", "OWNERSHIP_STRUCTURE")));
    }

    @Test
    void anOwnerFoundByOwnershipNeedsAPercentage() {
        var parties = List.of(party("AUTHORISED_PERSON", "A. Director", null, null, true), party("BENEFICIAL_OWNER", "B. Owner", null, "OWNERSHIP", true));
        assertTrue(KycRules.missing(lowRisk("COMPANY"), parties, items("ADDRESS", "REGISTRATION", "OWNERSHIP_STRUCTURE")).stream().anyMatch(s -> s.contains("percentage for B. Owner")));
    }

    @Test
    void aTrustNeedsDeedTrusteesFounderBeneficiariesAndAnAuthorisedPerson() {
        assertEquals(6, KycRules.missing(lowRisk("TRUST"), List.of(), items()).size());
        var parties = List.of(party("TRUSTEE", "T", null, null, true), party("FOUNDER", "F", null, null, true), party("BENEFICIARY", "B", null, null, true),
                party("AUTHORISED_PERSON", "A", null, null, true));
        assertEquals(List.of(), KycRules.missing(lowRisk("TRUST"), parties, items("ADDRESS", "TRUST_DEED")));
    }

    @Test
    void aPartnershipNeedsEveryPartnerAndAtLeastTwo() {
        var one = List.of(party("PARTNER", "P1", null, null, true), party("AUTHORISED_PERSON", "A", null, null, true));
        assertTrue(KycRules.missing(lowRisk("PARTNERSHIP"), one, items("ADDRESS")).get(0).contains("at least two"));
        var two = List.of(party("PARTNER", "P1", null, null, true), party("PARTNER", "P2", null, null, true), party("AUTHORISED_PERSON", "A", null, null, true));
        assertEquals(List.of(), KycRules.missing(lowRisk("PARTNERSHIP"), two, items("ADDRESS")));
    }

    @Test
    void everyPersonRecordedMustBeVerified() {
        var parties = List.of(party("PARTNER", "P1", null, null, true), party("PARTNER", "P2", null, null, false), party("AUTHORISED_PERSON", "A", null, null, true));
        List<String> m = KycRules.missing(lowRisk("PARTNERSHIP"), parties, items("ADDRESS"));
        assertEquals(1, m.size());
        assertTrue(m.get(0).contains("P2"));
    }

    // ---- where a client stands -----------------------------------------------------------------------------------------

    @Test
    void overviewStatusReadsAsTheFirmWouldExpect() {
        assertEquals("NOT_STARTED", KycRules.overviewStatus(null, null, TODAY));
        assertEquals("DRAFT", KycRules.overviewStatus(null, "DRAFT", TODAY));
        assertEquals("SUBMITTED", KycRules.overviewStatus(null, "SUBMITTED", TODAY));
        assertEquals("VERIFIED", KycRules.overviewStatus(TODAY.plusDays(200), null, TODAY));
        assertEquals("DUE_SOON", KycRules.overviewStatus(TODAY.plusDays(30), null, TODAY));
        assertEquals("DUE_SOON", KycRules.overviewStatus(TODAY, null, TODAY));
        assertEquals("EXPIRED", KycRules.overviewStatus(TODAY.minusDays(1), null, TODAY));
    }

    @Test
    void aReviewInProgressDoesNotHideACurrentVerification() {
        assertEquals("VERIFIED", KycRules.overviewStatus(TODAY.plusDays(200), "DRAFT", TODAY));
        assertEquals("DRAFT", KycRules.overviewStatus(TODAY.minusDays(1), "DRAFT", TODAY));
    }
}
