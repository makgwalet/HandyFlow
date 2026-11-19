package za.co.handyflow.platform.legalcompliance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.PrivacyPartyRules;
import za.co.handyflow.platform.legalcompliance.application.internal.PrivacyPartyRules.Expiry;
import za.co.handyflow.platform.legalcompliance.domain.model.Criticality;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskLevel;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PrivacyPartyRulesTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 10);

    @Test @DisplayName("PAIA: due date is 30 days after receipt; the extension cap is 30 more")
    void paiaDates() {
        assertEquals(LocalDate.of(2026, 4, 9), PrivacyPartyRules.paiaDueDate(D));
        assertEquals(LocalDate.of(2026, 5, 9), PrivacyPartyRules.paiaMaxExtendedDate(PrivacyPartyRules.paiaDueDate(D)));
    }

    @Test @DisplayName("PAIA: the extended date counts once it is later than the due date")
    void paiaEffective() {
        LocalDate due = PrivacyPartyRules.paiaDueDate(D);
        assertEquals(due, PrivacyPartyRules.paiaEffectiveDue(due, null));
        assertEquals(due.plusDays(10), PrivacyPartyRules.paiaEffectiveDue(due, due.plusDays(10)));
        assertEquals(due, PrivacyPartyRules.paiaEffectiveDue(due, due.minusDays(1)));
    }

    @Test @DisplayName("PAIA: overdue only when undecided and past the effective date; due soon within 5 days")
    void paiaFlags() {
        LocalDate due = LocalDate.of(2026, 4, 9);
        assertFalse(PrivacyPartyRules.paiaOverdue(due, false, due));
        assertTrue(PrivacyPartyRules.paiaOverdue(due, false, due.plusDays(1)));
        assertFalse(PrivacyPartyRules.paiaOverdue(due, true, due.plusDays(30)));
        assertTrue(PrivacyPartyRules.paiaDueSoon(due, false, due.minusDays(5)));
        assertFalse(PrivacyPartyRules.paiaDueSoon(due, false, due.minusDays(6)));
        assertFalse(PrivacyPartyRules.paiaDueSoon(due, false, due.plusDays(1)));
    }

    @Test @DisplayName("Filings: overdue and due-soon windows")
    void filings() {
        assertTrue(PrivacyPartyRules.filingOverdue(D, false, D.plusDays(1)));
        assertFalse(PrivacyPartyRules.filingOverdue(D, true, D.plusDays(1)));
        assertTrue(PrivacyPartyRules.filingDueSoon(D, false, D.minusDays(30)));
        assertFalse(PrivacyPartyRules.filingDueSoon(D, false, D.minusDays(31)));
    }

    @Test @DisplayName("FICA: review cadence follows risk, and a PEP is always the shortest")
    void fica() {
        assertEquals(6, PrivacyPartyRules.ficaReviewMonths(RiskLevel.HIGH, false));
        assertEquals(6, PrivacyPartyRules.ficaReviewMonths(RiskLevel.LOW, true));
        assertEquals(12, PrivacyPartyRules.ficaReviewMonths(RiskLevel.MEDIUM, false));
        assertEquals(24, PrivacyPartyRules.ficaReviewMonths(RiskLevel.LOW, false));
        assertEquals(LocalDate.of(2026, 9, 10), PrivacyPartyRules.ficaNextReview(D, RiskLevel.HIGH, false));
    }

    @Test @DisplayName("Third parties: default review months by criticality; expiry states")
    void thirdParty() {
        assertEquals(6, PrivacyPartyRules.defaultReviewMonths(Criticality.CRITICAL));
        assertEquals(24, PrivacyPartyRules.defaultReviewMonths(Criticality.LOW));
        assertEquals(Expiry.NO_EXPIRY, PrivacyPartyRules.expiry(null, D));
        assertEquals(Expiry.EXPIRED, PrivacyPartyRules.expiry(D.minusDays(1), D));
        assertEquals(Expiry.EXPIRING, PrivacyPartyRules.expiry(D, D));
        assertEquals(Expiry.EXPIRING, PrivacyPartyRules.expiry(D.plusDays(30), D));
        assertEquals(Expiry.VALID, PrivacyPartyRules.expiry(D.plusDays(31), D));
        assertTrue(PrivacyPartyRules.reviewOverdue(D, D.plusDays(1)));
        assertFalse(PrivacyPartyRules.reviewOverdue(null, D));
    }
}
