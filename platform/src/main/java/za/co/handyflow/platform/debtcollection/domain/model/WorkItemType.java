package za.co.handyflow.platform.debtcollection.domain.model;

import java.util.EnumSet;
import java.util.Set;

import static za.co.handyflow.platform.debtcollection.domain.model.WorkItemDisposition.*;

/** What kind of action a work item asks a collector to take. Each type declares which dispositions may clear it. */
public enum WorkItemType {
    /** Raised by the PTP engine when a promise is broken or only partly kept. */
    BROKEN_PTP(40, EnumSet.of(CONTACT_MADE, PTP_CAPTURED, PAYMENT_CONFIRMED, ESCALATED_TO_LEGAL, WRITE_OFF_RECOMMENDED, OTHER)),
    /** Raised by the PTP engine ahead of a promise's due date: remind the debtor / verify payment. */
    PTP_DUE(25, EnumSet.of(PAYMENT_CONFIRMED, CONTACT_MADE, NO_ANSWER_RETRY_SCHEDULED, NO_ACTION_REQUIRED, OTHER)),
    INSTALLMENT_OVERDUE(35, EnumSet.of(PAYMENT_CONFIRMED, CONTACT_MADE, PTP_CAPTURED, ESCALATED_TO_LEGAL, WRITE_OFF_RECOMMENDED, OTHER)),
    LEGAL_REVIEW(35, EnumSet.of(ESCALATED_TO_LEGAL, DISPUTE_LOGGED, WRITE_OFF_RECOMMENDED, NO_ACTION_REQUIRED, OTHER)),
    DISPUTE_REVIEW(25, EnumSet.of(DISPUTE_LOGGED, CONTACT_MADE, PAYMENT_CONFIRMED, WRITE_OFF_RECOMMENDED, NO_ACTION_REQUIRED, OTHER)),
    INSTALLMENT_DUE(25, EnumSet.of(PAYMENT_CONFIRMED, CONTACT_MADE, NO_ANSWER_RETRY_SCHEDULED, NO_ACTION_REQUIRED, OTHER)),
    FOLLOW_UP_DUE(15, EnumSet.of(CONTACT_MADE, NO_ANSWER_RETRY_SCHEDULED, PTP_CAPTURED, PLAN_RECORDED, PAYMENT_CONFIRMED, ESCALATED_TO_LEGAL, OTHER)),
    MANUAL(10, EnumSet.allOf(WorkItemDisposition.class));

    private final int baseScore;
    private final Set<WorkItemDisposition> allowed;

    WorkItemType(int baseScore, Set<WorkItemDisposition> allowed) {
        this.baseScore = baseScore;
        this.allowed = allowed;
    }

    public int baseScore() { return baseScore; }
    public Set<WorkItemDisposition> allowedDispositions() { return allowed; }
}
