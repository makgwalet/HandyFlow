package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The negotiation of one contract's wording with its counterparties. No row means NOT_STARTED.
 * OPEN → STAFF_REVIEW (proposals waiting) → CHANGES_REQUIRED (accepted, not yet applied) → READY_FOR_SIGNATURE → CLOSED.
 * The first three are recalculated from the proposals; READY_FOR_SIGNATURE pins the final text and CLOSED ends negotiation.
 */
@Entity
@Table(name = "contract_negotiations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNegotiation {
    public static final String OPEN = "OPEN", STAFF_REVIEW = "STAFF_REVIEW", CHANGES_REQUIRED = "CHANGES_REQUIRED",
            READY = "READY_FOR_SIGNATURE", CLOSED = "CLOSED";

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    private String status;
    @Column(name = "response_deadline") private LocalDate responseDeadline;
    @Column(name = "base_version_no") private int baseVersionNo;
    @Column(name = "final_version_no") private Integer finalVersionNo;
    @Column(name = "final_body_hash") private String finalBodyHash;
    @Column(name = "opened_by") private UUID openedBy;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "ready_by") private UUID readyBy;
    @Column(name = "ready_at") private Instant readyAt;
    @Column(name = "closed_by") private UUID closedBy;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "close_reason") private String closeReason;
    @Column(name = "last_reminded_on") private LocalDate lastRemindedOn;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractNegotiation open(UUID tenantId, UUID contractId, int baseVersionNo, LocalDate deadline, UUID by, LocalDate today) {
        if (deadline != null && deadline.isBefore(today)) throw new IllegalArgumentException("The response deadline cannot be in the past");
        ContractNegotiation n = new ContractNegotiation();
        n.id = UUID.randomUUID();
        n.tenantId = tenantId;
        n.contractId = contractId;
        n.baseVersionNo = baseVersionNo;
        n.responseDeadline = deadline;
        n.status = OPEN;
        n.openedBy = by;
        n.openedAt = Instant.now();
        n.updatedAt = n.openedAt;
        return n;
    }

    /** True while counterparties can still propose and comment. */
    public boolean acceptsCounterpartyInput(LocalDate today) {
        return (OPEN.equals(status) || STAFF_REVIEW.equals(status) || CHANGES_REQUIRED.equals(status))
                && (responseDeadline == null || !today.isAfter(responseDeadline));
    }

    public static final String REMIND_DEADLINE_SOON = "DEADLINE_SOON", REMIND_DEADLINE_PASSED = "DEADLINE_PASSED", REMIND_PENDING = "PENDING";

    /**
     * Why staff should be reminded today, or null. Deadline within two days: once a day. Deadline passed: once a week. Proposals
     * that have waited since before today for a decision: once a day. A deadline reason wins over the waiting one. Never for a
     * negotiation that is ready or closed.
     */
    public String reminderReason(LocalDate today, Instant oldestPendingProposal, ZoneId zone) {
        if (!isActive()) return null;
        boolean remindedToday = today.equals(lastRemindedOn);
        if (responseDeadline != null) {
            long days = ChronoUnit.DAYS.between(today, responseDeadline);
            if (days < 0) return lastRemindedOn == null || ChronoUnit.DAYS.between(lastRemindedOn, today) >= 7 ? REMIND_DEADLINE_PASSED : null;
            if (days <= 2 && !remindedToday) return REMIND_DEADLINE_SOON;
        }
        if (oldestPendingProposal != null && !remindedToday && oldestPendingProposal.isBefore(today.atStartOfDay(zone).toInstant())) return REMIND_PENDING;
        return null;
    }

    public void markReminded(LocalDate today) { this.lastRemindedOn = today; }

    public boolean isActive() { return OPEN.equals(status) || STAFF_REVIEW.equals(status) || CHANGES_REQUIRED.equals(status); }

    public void setDeadline(LocalDate deadline, LocalDate today) {
        if (CLOSED.equals(status)) throw new IllegalStateException("This negotiation is closed");
        if (deadline != null && deadline.isBefore(today)) throw new IllegalArgumentException("The response deadline cannot be in the past");
        this.responseDeadline = deadline;
        this.updatedAt = Instant.now();
    }

    /** Recalculates OPEN / STAFF_REVIEW / CHANGES_REQUIRED from the proposals. Ready and closed negotiations are left alone. */
    public void refresh(long unresolved, long acceptedNotApplied) {
        if (!isActive()) return;
        String next = unresolved > 0 ? STAFF_REVIEW : acceptedNotApplied > 0 ? CHANGES_REQUIRED : OPEN;
        if (!next.equals(status)) { status = next; updatedAt = Instant.now(); }
    }

    /** Pins the final text. Only possible when nothing is waiting for review and nothing accepted is still unapplied. */
    public void markReady(long unresolved, long acceptedNotApplied, int versionNo, String bodyHash, UUID by) {
        if (!isActive()) throw new IllegalStateException("Only an open negotiation can be marked ready for signature (this one is " + status + ")");
        if (unresolved > 0) throw new IllegalStateException(unresolved + " proposal(s) still need a decision");
        if (acceptedNotApplied > 0) throw new IllegalStateException(acceptedNotApplied + " accepted proposal(s) have not been applied to a new revision yet");
        status = READY;
        finalVersionNo = versionNo;
        finalBodyHash = bodyHash;
        readyBy = by;
        readyAt = Instant.now();
        updatedAt = readyAt;
    }

    /** Back to open, discarding the pinned final text (for example to take one more round of comments before signing). */
    public void reopen() {
        if (!READY.equals(status) && !CLOSED.equals(status)) throw new IllegalStateException("This negotiation is already open");
        status = OPEN;
        finalVersionNo = null; finalBodyHash = null; readyBy = null; readyAt = null; closedBy = null; closedAt = null; closeReason = null;
        updatedAt = Instant.now();
    }

    public void close(String reason, UUID by) {
        if (CLOSED.equals(status)) throw new IllegalStateException("This negotiation is already closed");
        status = CLOSED;
        closeReason = reason;
        closedBy = by;
        closedAt = Instant.now();
        updatedAt = closedAt;
    }
}
