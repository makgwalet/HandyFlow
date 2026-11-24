package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A person who cannot approve from one day to another (both included). Their waiting approvals are routed to the delegate, if there is one. */
@Entity
@Table(name = "contract_approver_absences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractApproverAbsence {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "from_date") private LocalDate fromDate;
    @Column(name = "to_date") private LocalDate toDate;
    @Column(name = "delegate_user_id") private UUID delegateUserId;
    private String reason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancelled_by") private UUID cancelledBy;

    public static ContractApproverAbsence create(UUID tenantId, UUID userId, LocalDate from, LocalDate to, UUID delegate, String reason, UUID by) {
        if (userId == null) throw new IllegalArgumentException("Choose who is away");
        if (from == null || to == null) throw new IllegalArgumentException("Give the first and last day away");
        if (to.isBefore(from)) throw new IllegalArgumentException("The last day away cannot be before the first");
        if (delegate != null && delegate.equals(userId)) throw new IllegalArgumentException("Someone cannot cover for themselves");
        ContractApproverAbsence a = new ContractApproverAbsence();
        a.id = UUID.randomUUID(); a.tenantId = tenantId; a.userId = userId; a.fromDate = from; a.toDate = to; a.delegateUserId = delegate;
        a.reason = reason == null || reason.isBlank() ? null : reason.trim().length() > 200 ? reason.trim().substring(0, 200) : reason.trim();
        a.createdBy = by; a.createdAt = Instant.now();
        return a;
    }

    public boolean isCancelled() { return cancelledAt != null; }

    public void cancel(UUID by) {
        if (isCancelled()) throw new IllegalStateException("This has already been cancelled");
        this.cancelledAt = Instant.now(); this.cancelledBy = by;
    }

    public EscalationRules.Absence toRule() { return new EscalationRules.Absence(userId, fromDate, toDate, delegateUserId); }
}
