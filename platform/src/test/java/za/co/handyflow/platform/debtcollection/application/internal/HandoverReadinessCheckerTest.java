package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Facts;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Finding;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.NoticeFact;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Severity;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CreditAgreementType;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeDeliveryStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeReviewStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandoverReadinessCheckerTest {

    private final HandoverReadinessChecker checker = new HandoverReadinessChecker();

    private static Facts ready(CreditAgreementType type, CaseStatus status, List<NoticeFact> notices) {
        return new Facts(status, new BigDecimal("100.00"), type, true, true, false, false, true, notices);
    }

    private static boolean has(List<Finding> f, String code, Severity s) {
        return f.stream().anyMatch(x -> x.code().equals(code) && x.severity() == s);
    }

    @Test
    void fullyPreparedCommercialCaseHasNoFindings() {
        List<Finding> f = checker.check(ready(CreditAgreementType.OTHER_COMMERCIAL, CaseStatus.DEMAND_SENT, List.of()));
        assertTrue(f.isEmpty());
        assertFalse(HandoverReadinessChecker.hasBlocking(f));
    }

    @Test
    void disputedCaseIsBlocked() {
        List<Finding> f = checker.check(ready(CreditAgreementType.OTHER_COMMERCIAL, CaseStatus.DISPUTED, List.of()));
        assertTrue(has(f, "CASE_DISPUTED", Severity.BLOCKING));
    }

    @Test
    void terminalOrAlreadyHandedCaseIsBlocked() {
        assertTrue(has(checker.check(ready(CreditAgreementType.OTHER_COMMERCIAL, CaseStatus.CLOSED, List.of())), "CASE_NOT_ACTIVE", Severity.BLOCKING));
        assertTrue(has(checker.check(ready(CreditAgreementType.OTHER_COMMERCIAL, CaseStatus.HANDED_TO_LEGAL, List.of())), "CASE_NOT_ACTIVE", Severity.BLOCKING));
    }

    @Test
    void unclassifiedAgreementAndMissingReviewsAreBlocked() {
        Facts f = new Facts(CaseStatus.OPEN, BigDecimal.ZERO, CreditAgreementType.UNKNOWN, false, false, false, false, true, List.of());
        List<Finding> out = checker.check(f);
        assertTrue(has(out, "NO_OUTSTANDING", Severity.BLOCKING));
        assertTrue(has(out, "AGREEMENT_TYPE_UNKNOWN", Severity.BLOCKING));
        assertTrue(has(out, "PRESCRIPTION_NOT_REVIEWED", Severity.BLOCKING));
        assertTrue(has(out, "CREDITOR_AUTHORITY_MISSING", Severity.BLOCKING));
    }

    @Test
    void ncaAgreementNeedsAnAcceptedSection129Notice() {
        List<Finding> none = checker.check(ready(CreditAgreementType.NCA_CREDIT_AGREEMENT, CaseStatus.OPEN, List.of()));
        assertTrue(has(none, "NCA_S129_NOT_ACCEPTED", Severity.BLOCKING));

        List<Finding> unreviewed = checker.check(ready(CreditAgreementType.NCA_CREDIT_AGREEMENT, CaseStatus.OPEN,
                List.of(new NoticeFact(NoticeType.SECTION_129, NoticeReviewStatus.NOT_REVIEWED, NoticeDeliveryStatus.DELIVERED))));
        assertTrue(has(unreviewed, "NCA_S129_NOT_ACCEPTED", Severity.BLOCKING));
    }

    @Test
    void acceptedButUndeliveredSection129IsOnlyAWarning() {
        List<Finding> f = checker.check(ready(CreditAgreementType.NCA_CREDIT_AGREEMENT, CaseStatus.OPEN,
                List.of(new NoticeFact(NoticeType.SECTION_129, NoticeReviewStatus.ACCEPTED, NoticeDeliveryStatus.PENDING))));
        assertFalse(HandoverReadinessChecker.hasBlocking(f));
        assertTrue(has(f, "S129_DELIVERY_UNCONFIRMED", Severity.WARNING));
    }

    @Test
    void commercialAgreementIgnoresSection129Rule() {
        assertFalse(has(checker.check(ready(CreditAgreementType.OTHER_COMMERCIAL, CaseStatus.OPEN, List.of())), "NCA_S129_NOT_ACCEPTED", Severity.BLOCKING));
    }

    @Test
    void openPromiseActivePlanAndMissingDemandAreWarnings() {
        Facts f = new Facts(CaseStatus.PAYMENT_PLAN_ACTIVE, new BigDecimal("50"), CreditAgreementType.OTHER_COMMERCIAL,
                true, true, true, true, false, List.of());
        List<Finding> out = checker.check(f);
        assertEquals(3, out.size());
        assertFalse(HandoverReadinessChecker.hasBlocking(out));
    }
}
