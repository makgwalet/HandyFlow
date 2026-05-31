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
        c.status       = "PENDING";
        c.notes        = notes;
        c.createdAt    = Instant.now();
        c.updatedAt    = Instant.now();
        return c;
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

    public void markReimbursed() {
        if (!"APPROVED".equals(status))
            throw new IllegalStateException("Only APPROVED claims can be reimbursed");
        this.status        = "REIMBURSED";
        this.reimbursedAt  = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public void linkJournalEntry(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
        this.updatedAt      = Instant.now();
    }
}