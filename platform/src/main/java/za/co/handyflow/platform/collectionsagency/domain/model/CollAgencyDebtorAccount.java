package za.co.handyflow.platform.collectionsagency.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One debtor's placed account within a creditor client's portfolio — the
 * agency's unit of collection work. Scoped to a client + debtor, not to
 * an internal case-escalation concept the way debtcollection.
 * DebtCollectionCase is — this account exists because the CLIENT placed
 * it, not because the agency's own internal reminders failed.
 * <p>
 * originalCreditorName is captured explicitly (not always assumed equal
 * to the client's tradingName) because the client placing this debt may
 * itself be a downstream assignee — e.g. a bank recovering on behalf of
 * an insurer, or a debt-purchase entity — and NCA disclosure requires
 * naming the actual original creditor, not just whoever handed the file
 * to the agency. Defaults to the client's tradingName at the service
 * layer when not supplied, but is stored per-account so it survives if
 * the client record is later renamed.
 * <p>
 * currentBalance starts at originalDebtAmount and is only ever reduced
 * by CollAgencyTrustTransactionService when a debtor payment is
 * recorded — this entity itself never touches trust money, it just
 * tracks what's still owed.
 */
@Entity
@Table(name = "collagency_debtor_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyDebtorAccount {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "placement_batch_id")
    private UUID placementBatchId;

    @Column(name = "account_reference")
    private String accountReference; // the client's own reference number for this debt

    @Column(name = "debtor_name", nullable = false)
    private String debtorName;

    @Column(name = "debtor_id_number")
    private String debtorIdNumber;

    @Column(name = "debtor_email")
    private String debtorEmail;

    @Column(name = "debtor_phone")
    private String debtorPhone;

    @Column(name = "debtor_address", columnDefinition = "TEXT")
    private String debtorAddress;

    @Column(name = "original_creditor_name", nullable = false)
    private String originalCreditorName;

    @Column(name = "original_debt_date")
    private LocalDate originalDebtDate;

    @Column(name = "original_debt_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalDebtAmount;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "status", nullable = false)
    private String status = "PLACED";
    // PLACED | IN_PROGRESS | PAYMENT_PLAN_ACTIVE | DISPUTED | RECOVERED |
    // RETURNED_TO_CLIENT | WRITTEN_OFF | CLOSED

    @Column(name = "assigned_collector_id")
    private UUID assignedCollectorId;

    @Column(name = "placed_date", nullable = false)
    private LocalDate placedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    private static final java.util.Set<String> TERMINAL_STATUSES =
            java.util.Set.of("RECOVERED", "RETURNED_TO_CLIENT", "WRITTEN_OFF", "CLOSED");

    public static CollAgencyDebtorAccount create(UUID tenantId, UUID clientId, UUID placementBatchId,
                                                  String accountReference, String debtorName, String debtorIdNumber,
                                                  String debtorEmail, String debtorPhone, String debtorAddress,
                                                  String originalCreditorName, LocalDate originalDebtDate,
                                                  BigDecimal originalDebtAmount, LocalDate placedDate,
                                                  String notes) {
        if (debtorName == null || debtorName.isBlank()) {
            throw new IllegalArgumentException("debtorName is required");
        }
        if (originalCreditorName == null || originalCreditorName.isBlank()) {
            throw new IllegalArgumentException("originalCreditorName is required — NCA disclosure requires naming it on every contact");
        }
        if (originalDebtAmount == null || originalDebtAmount.signum() <= 0) {
            throw new IllegalArgumentException("originalDebtAmount must be positive");
        }
        CollAgencyDebtorAccount a = new CollAgencyDebtorAccount();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.placementBatchId = placementBatchId;
        a.accountReference = accountReference;
        a.debtorName = debtorName;
        a.debtorIdNumber = debtorIdNumber;
        a.debtorEmail = debtorEmail;
        a.debtorPhone = debtorPhone;
        a.debtorAddress = debtorAddress;
        a.originalCreditorName = originalCreditorName;
        a.originalDebtDate = originalDebtDate;
        a.originalDebtAmount = originalDebtAmount;
        a.currentBalance = originalDebtAmount;
        a.status = "PLACED";
        a.placedDate = placedDate != null ? placedDate : LocalDate.now();
        a.notes = notes;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void assign(UUID collectorId) {
        requireNotTerminal();
        this.assignedCollectorId = collectorId;
        this.updatedAt = Instant.now();
    }

    /** Workflow status changes. CLOSED-family statuses (RECOVERED/RETURNED_TO_CLIENT/WRITTEN_OFF/CLOSED) are terminal — see close()-style guard. */
    public void advanceStatus(String newStatus) {
        requireNotTerminal();
        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("newStatus is required");
        }
        this.status = newStatus;
        if (TERMINAL_STATUSES.contains(newStatus)) {
            this.closedDate = LocalDate.now();
        }
        this.updatedAt = Instant.now();
    }

    /** Reduces currentBalance when a debtor payment is recorded — called only by the trust-transaction service, never directly. Auto-advances to RECOVERED when the balance reaches zero. */
    public void applyPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.currentBalance = this.currentBalance.subtract(amount);
        if (this.currentBalance.signum() <= 0) {
            this.currentBalance = BigDecimal.ZERO;
            this.status = "RECOVERED";
            this.closedDate = LocalDate.now();
        } else if ("PLACED".equals(this.status)) {
            this.status = "IN_PROGRESS";
        }
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void requireNotTerminal() {
        if (TERMINAL_STATUSES.contains(this.status)) {
            throw new IllegalStateException("Cannot change a debtor account that is already " + this.status);
        }
    }
}
