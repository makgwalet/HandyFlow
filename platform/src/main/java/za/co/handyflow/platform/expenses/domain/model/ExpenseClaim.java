package za.co.handyflow.platform.expenses.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense_claims")
@Getter
@NoArgsConstructor
public class ExpenseClaim {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "claim_number")  String claimNumber;
    @Column(name = "employee_id")   UUID employeeId;
    @Column(name = "submitted_by")  UUID submittedBy;
    @Column(name = "employee_name") String employeeName;
    @Column(name = "claim_date")    LocalDate claimDate;
    String category;
    String description;
    BigDecimal amount;
    String currency = "ZAR";
    @Column(name = "receipt_url")   String receiptUrl;
    String status = "PENDING";
    @Column(name = "approved_by")    UUID approvedBy;
    @Column(name = "approved_at")    Instant approvedAt;
    @Column(name = "rejection_reason") String rejectionReason;
    @Column(name = "reimbursed_at")  Instant reimbursedAt;
    @Column(name = "journal_entry_id") UUID journalEntryId;
    String notes;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;
    // Finance patch F7: the claim's life (draft, submissions, withdrawal, payout).
    @Column(name = "submitted_at")     Instant submittedAt;
    @Column(name = "submission_count") int     submissionCount;
    @Column(name = "cancelled_at")     Instant cancelledAt;
    @Column(name = "reimbursed_by")    UUID    reimbursedBy;
    @Column(name = "reimbursement_bank_account_id") UUID reimbursementBankAccountId;
    @Column(name = "reimbursement_reference")       String reimbursementReference;
    @Column(name = "reimbursement_journal_id")      UUID reimbursementJournalId;
    // Finance patch F18: what the policy and duplicate checks raised at the last submission, one per line.
    @Column(name = "policy_flags")                  String policyFlags;

    public static ExpenseClaim create(TenantId tenantId, String claimNumber,
                                      UUID employeeId, UUID submittedBy,
                                      String employeeName, LocalDate claimDate,
                                      String category, String description,
                                      BigDecimal amount, String receiptUrl,
                                      String notes) {
        ExpenseClaim c = new ExpenseClaim();
        c.id           = UUID.randomUUID();
        c.tenantId     = tenantId.getValue();
        c.claimNumber  = claimNumber;
        c.employeeId   = employeeId;
        c.submittedBy  = submittedBy;
        c.employeeName = employeeName;
        c.claimDate    = claimDate;
        c.category     = category;
        c.description  = description;
        c.amount       = amount;
        c.currency     = "ZAR";
        c.receiptUrl   = receiptUrl;
        c.status       = "DRAFT";
        c.notes        = notes;
        c.createdAt    = Instant.now();
        c.updatedAt    = Instant.now();
        return c;
    }

    /** Changes a draft or rejected claim (finance patch F7). */
    public void edit(UUID employeeId, String employeeName, LocalDate claimDate, String category, String description,
                     BigDecimal amount, String receiptUrl, String notes) {
        require(za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action.EDIT);
        this.employeeId   = employeeId;
        this.employeeName = employeeName;
        this.claimDate    = claimDate;
        this.category     = category;
        this.description  = description;
        this.amount       = amount;
        this.receiptUrl   = receiptUrl;
        this.notes        = notes;
        this.updatedAt    = Instant.now();
    }

    /** Sends a draft, or a rejected claim that was fixed, for approval (finance patch F7). */
    public void submit() {
        require(za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action.SUBMIT);
        this.status          = "PENDING";
        this.rejectionReason = null;
        this.approvedBy      = null;
        this.approvedAt      = null;
        this.submittedAt     = Instant.now();
        this.submissionCount = this.submissionCount + 1;
        this.updatedAt       = this.submittedAt;
    }

    /** The claimant withdraws a draft or a claim waiting for approval (finance patch F7). */
    public void withdraw() {
        require(za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action.WITHDRAW);
        this.status      = "CANCELLED";
        this.cancelledAt = Instant.now();
        this.updatedAt   = this.cancelledAt;
    }

    private void require(za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action action) {
        String problem = za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.problem(action, status);
        if (problem != null) throw new IllegalStateException(problem);
    }

    public void approve(UUID approvedBy) {
        if (!"PENDING".equals(status))
            throw new IllegalStateException("Only PENDING claims can be approved");
        this.status     = "APPROVED";
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void reject(UUID approvedBy, String reason) {
        if (!"PENDING".equals(status))
            throw new IllegalStateException("Only PENDING claims can be rejected");
        this.status          = "REJECTED";
        this.approvedBy      = approvedBy;
        this.rejectionReason = reason;
        this.approvedAt      = Instant.now();
        this.updatedAt       = Instant.now();
    }

    /** Records the payout (finance patch F7): who paid it, from which bank account, the reference and its journal. */
    public void markReimbursed(UUID by, UUID bankAccountId, String reference, UUID journalId) {
        require(za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action.REIMBURSE);
        this.status                     = "REIMBURSED";
        this.reimbursedAt               = Instant.now();
        this.reimbursedBy               = by;
        this.reimbursementBankAccountId = bankAccountId;
        this.reimbursementReference     = reference;
        this.reimbursementJournalId     = journalId;
        this.updatedAt                  = this.reimbursedAt;
    }

    /** Keeps what the policy checks raised, for the approver to see (finance patch F18). */
    public void recordPolicyFlags(String flags) {
        this.policyFlags = flags == null || flags.isBlank() ? null : flags;
    }

    public void linkJournalEntry(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
        this.updatedAt      = Instant.now();
    }
}