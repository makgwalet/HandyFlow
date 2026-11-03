package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.CostEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of the project cost ledger. APPEND-ONLY: no setters, no update path.
 * A mistake is corrected by a reversing entry (reversalOf), never by editing history.
 * A budget line's committed / actual figures are always the SUM of its entries.
 */
@Entity
@Table(name = "project_cost_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCostEntry {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "budget_line_id", nullable = false)
    private UUID budgetLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private CostEntryType entryType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    @Column(name = "description")
    private String description;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "reversal_of")
    private UUID reversalOf;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "posted_by_name")
    private String postedByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ProjectCostEntry create(UUID tenantId, UUID projectId, UUID budgetLineId,
                                          CostEntryType type, BigDecimal amount,
                                          String sourceType, String sourceRef, String description,
                                          LocalDate entryDate, UUID reversalOf, String idempotencyKey,
                                          UUID postedBy, String postedByName) {
        ProjectCostEntry e = new ProjectCostEntry();
        e.id             = UUID.randomUUID();
        e.tenantId       = tenantId;
        e.projectId      = projectId;
        e.budgetLineId   = budgetLineId;
        e.entryType      = type;
        e.amount         = amount;
        e.sourceType     = sourceType;
        e.sourceRef      = sourceRef;
        e.description    = description;
        e.entryDate      = entryDate;
        e.reversalOf     = reversalOf;
        e.idempotencyKey = idempotencyKey;
        e.postedBy       = postedBy;
        e.postedByName   = postedByName;
        e.createdAt      = Instant.now();
        return e;
    }
}