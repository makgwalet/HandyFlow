package za.co.handyflow.platform.debtcollection.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CreditAgreementType;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeDeliveryStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeReviewStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pre-handover checks over prepared facts (pure, no database). BLOCKING findings stop submission, approval and
 * transfer — they are re-run at each step because facts change (a dispute can be raised after approval).
 * WARNINGs are shown to the approver but do not stop anything.
 * <p>
 * This is a controlled-process aid, not legal advice: it checks that required human steps (classification,
 * prescription review, creditor authority, section 129 review where the agreement is classified as NCA) have been
 * recorded — it never decides they are legally sufficient.
 */
@Component
public class HandoverReadinessChecker {

    public enum Severity { BLOCKING, WARNING }

    public record Finding(String code, Severity severity, String message) {}

    public record NoticeFact(NoticeType type, NoticeReviewStatus review, NoticeDeliveryStatus delivery) {}

    public record Facts(CaseStatus caseStatus, BigDecimal liveOutstanding, CreditAgreementType agreementType,
                        boolean prescriptionReviewed, boolean creditorAuthorityRecorded, boolean openPtp,
                        boolean activePlan, boolean demandRecorded, List<NoticeFact> notices) {}

    private static final Set<CaseStatus> HANDOVER_ELIGIBLE =
            EnumSet.of(CaseStatus.OPEN, CaseStatus.DEMAND_SENT, CaseStatus.PAYMENT_PLAN_ACTIVE);

    public List<Finding> check(Facts f) {
        List<Finding> out = new ArrayList<>();
        if (f.caseStatus() == CaseStatus.DISPUTED) {
            out.add(block("CASE_DISPUTED", "The case is DISPUTED — resolve or record the outcome of the dispute before handover"));
        } else if (!HANDOVER_ELIGIBLE.contains(f.caseStatus())) {
            out.add(block("CASE_NOT_ACTIVE", "The case is " + f.caseStatus() + " and cannot be handed over"));
        }
        if (f.liveOutstanding() == null || f.liveOutstanding().signum() <= 0) {
            out.add(block("NO_OUTSTANDING", "Invoicing shows nothing outstanding on the linked invoices"));
        }
        if (f.agreementType() == CreditAgreementType.UNKNOWN) {
            out.add(block("AGREEMENT_TYPE_UNKNOWN", "Classify the agreement (NCA credit agreement or other commercial) — it decides which legal steps apply"));
        }
        if (!f.prescriptionReviewed()) {
            out.add(block("PRESCRIPTION_NOT_REVIEWED", "A prescription review has not been recorded"));
        }
        if (!f.creditorAuthorityRecorded()) {
            out.add(block("CREDITOR_AUTHORITY_MISSING", "Record the creditor's authority (cession, mandate or contract) and/or attach evidence"));
        }
        if (f.agreementType() == CreditAgreementType.NCA_CREDIT_AGREEMENT) {
            List<NoticeFact> s129 = f.notices().stream().filter(n -> n.type() == NoticeType.SECTION_129).toList();
            if (s129.stream().noneMatch(n -> n.review() == NoticeReviewStatus.ACCEPTED)) {
                out.add(block("NCA_S129_NOT_ACCEPTED", "No section 129 notice has been recorded AND accepted by a legal reviewer"));
            } else if (s129.stream().noneMatch(n -> n.review() == NoticeReviewStatus.ACCEPTED
                    && n.delivery() == NoticeDeliveryStatus.DELIVERED)) {
                out.add(warn("S129_DELIVERY_UNCONFIRMED", "The accepted section 129 notice is not recorded as DELIVERED — confirm the delivery evidence"));
            }
        }
        if (f.openPtp()) out.add(warn("OPEN_PTP", "There is an open promise to pay on this case"));
        if (f.activePlan()) out.add(warn("ACTIVE_PAYMENT_PLAN", "There is an active payment plan on this case"));
        if (!f.demandRecorded()) out.add(warn("NO_DEMAND_RECORDED", "No demand (letter of demand notice or sent DEMAND communication) is recorded"));
        return out;
    }

    public static boolean hasBlocking(List<Finding> findings) {
        return findings.stream().anyMatch(x -> x.severity() == Severity.BLOCKING);
    }

    private static Finding block(String code, String msg) { return new Finding(code, Severity.BLOCKING, msg); }

    private static Finding warn(String code, String msg) { return new Finding(code, Severity.WARNING, msg); }
}
