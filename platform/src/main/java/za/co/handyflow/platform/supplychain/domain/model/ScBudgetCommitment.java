package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The budget impact of one approved PO, tagged with the dimensions it counts against. */
@Entity
@Table(name = "sc_budget_commitments")
@Getter
@NoArgsConstructor
public class ScBudgetCommitment {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "purchase_order_id", nullable = false, unique = true) UUID purchaseOrderId;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal amount;
    @Column(length = 100) String department;
    @Column(name = "cost_centre", length = 100) String costCentre;
    @Column(name = "project_ref", length = 100) String projectRef;
    @Column(name = "spend_date", nullable = false) LocalDate spendDate;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScBudgetCommitment create(UUID tenantId, UUID poId, BigDecimal amount, String department,
                                            String costCentre, String projectRef, LocalDate spendDate) {
        ScBudgetCommitment c = new ScBudgetCommitment();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.purchaseOrderId = poId;
        c.createdAt = Instant.now();
        c.restate(amount, department, costCentre, projectRef, spendDate);
        return c;
    }

    /** Re-states the commitment after a PO amendment (or a re-approval). */
    public void restate(BigDecimal amount, String department, String costCentre, String projectRef, LocalDate spendDate) {
        this.amount = amount;
        this.department = department;
        this.costCentre = costCentre;
        this.projectRef = projectRef;
        this.spendDate = spendDate;
        this.updatedAt = Instant.now();
    }
}
