package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single recorded contact attempt against a DebtCollectionCase's debtor —
 * the structured compliance trail (who contacted the debtor, when, how, and
 * what was agreed or refused). Deliberately append-only: no update/delete
 * methods, and no soft-delete columns, because a contact log is a record of
 * what actually happened, not an editable field set — the same reasoning
 * that makes a general ledger posting or an audit-trail entry immutable
 * elsewhere in this codebase. If an entry is wrong, the fix is a new
 * correcting entry, not an edit to the old one.
 * <p>
 * This is also the direct template for the Collections Agency variant's own
 * (necessarily much stricter — NCA third-party-collector disclosure rules)
 * contact log, so its shape here is deliberately a little richer than
 * strictly required for an original creditor, to make that reuse obvious.
 */
@Entity
@Table(name = "debtcollection_contact_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionContactLog extends AggregateRoot<CollectionContactLog> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "contact_date", nullable = false)
    private LocalDate contactDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", nullable = false, length = 20)
    private ContactMethod contactMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ContactOutcome outcome;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "promised_payment_date")
    private LocalDate promisedPaymentDate;

    @Column(name = "promised_payment_amount", precision = 15, scale = 2)
    private BigDecimal promisedPaymentAmount;

    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;

    @Column(name = "recorded_by_user_name", length = 255)
    private String recordedByUserName;

    public static CollectionContactLog record(TenantId tenantId, UUID caseId, LocalDate contactDate,
                                               ContactMethod contactMethod, ContactOutcome outcome, String notes,
                                               LocalDate promisedPaymentDate, BigDecimal promisedPaymentAmount,
                                               UUID recordedByUserId, String recordedByUserName) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId is required");
        }
        if (contactMethod == null || outcome == null) {
            throw new IllegalArgumentException("contactMethod and outcome are required");
        }
        if (outcome == ContactOutcome.PROMISE_TO_PAY && (promisedPaymentDate == null || promisedPaymentAmount == null)) {
            throw new IllegalArgumentException("promisedPaymentDate and promisedPaymentAmount are required when outcome is PROMISE_TO_PAY");
        }
        CollectionContactLog log = new CollectionContactLog();
        log.initTenantId(tenantId);
        log.caseId = caseId;
        log.contactDate = contactDate != null ? contactDate : LocalDate.now();
        log.contactMethod = contactMethod;
        log.outcome = outcome;
        log.notes = notes;
        log.promisedPaymentDate = promisedPaymentDate;
        log.promisedPaymentAmount = promisedPaymentAmount;
        log.recordedByUserId = recordedByUserId;
        log.recordedByUserName = recordedByUserName;
        return log;
    }
}
