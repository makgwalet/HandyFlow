package za.co.handyflow.platform.creative.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cre_jobs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CreJob {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id")  private UUID   customerId;
    @Column(name = "client_name",  nullable = false) private String clientName;
    @Column(name = "client_email") private String clientEmail;
    @Column(nullable = false)      private String title;
    @Column(name = "job_type", nullable = false) private String jobType = "OTHER";

    private String description;
    private String brief;

    @Column(nullable = false) private String status   = "BRIEFING";
    @Column(nullable = false) private String priority = "NORMAL";

    @Column(name = "due_date")      private LocalDate  dueDate;
    @Column(precision = 12, scale = 2) private BigDecimal budget;
    @Column(name = "quoted_amount", precision = 12, scale = 2) private BigDecimal quotedAmount;
    @Column(name = "invoice_id")    private UUID       invoiceId;

    private String notes;

    @Column(name = "assigned_to") private UUID    assignedTo;
    @Column(name = "created_by")  private UUID    createdBy;
    @Column(name = "created_at")  private Instant createdAt;
    @Column(name = "updated_at")  private Instant updatedAt;
    @Column(name = "deleted_at")  private Instant deletedAt;

    @Version private Long version;

    public static CreJob create(TenantId tenantId, UUID customerId, String clientName,
                                 String clientEmail, String title, String jobType,
                                 String description, String brief, String priority,
                                 LocalDate dueDate, BigDecimal budget, BigDecimal quotedAmount,
                                 UUID assignedTo, String notes, UUID createdBy) {
        CreJob j        = new CreJob();
        j.tenantId      = tenantId;
        j.customerId    = customerId;
        j.clientName    = clientName;
        j.clientEmail   = clientEmail;
        j.title         = title;
        j.jobType       = jobType != null ? jobType : "OTHER";
        j.description   = description;
        j.brief         = brief;
        j.priority      = priority != null ? priority : "NORMAL";
        j.dueDate       = dueDate;
        j.budget        = budget;
        j.quotedAmount  = quotedAmount;
        j.assignedTo    = assignedTo;
        j.notes         = notes;
        j.createdBy     = createdBy;
        j.status        = "BRIEFING";
        j.createdAt     = Instant.now();
        j.updatedAt     = Instant.now();
        return j;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void startWork()          { transition("IN_PROGRESS"); }
    public void sendForApproval()    { transition("AWAITING_APPROVAL"); }
    public void requestRevision()    { transition("IN_REVISION"); }
    public void markApproved()       { transition("APPROVED"); }
    public void markDelivered()      { transition("DELIVERED"); }
    public void markInvoiced(UUID invoiceId) {
        transition("INVOICED");
        this.invoiceId = invoiceId;
    }
    public void cancel()             { this.status = "CANCELLED"; touch(); }

    public void updateDetails(String title, String description, String brief,
                               String priority, LocalDate dueDate,
                               BigDecimal budget, BigDecimal quotedAmount,
                               UUID assignedTo, String notes,
                               String clientEmail) {
        if (title        != null) this.title        = title;
        if (description  != null) this.description  = description;
        if (brief        != null) this.brief        = brief;
        if (priority     != null) this.priority     = priority;
        if (dueDate      != null) this.dueDate      = dueDate;
        if (budget       != null) this.budget       = budget;
        if (quotedAmount != null) this.quotedAmount = quotedAmount;
        if (assignedTo   != null) this.assignedTo   = assignedTo;
        if (notes        != null) this.notes        = notes;
        if (clientEmail  != null) this.clientEmail  = clientEmail;
        touch();
    }

    private void transition(String newStatus) {
        this.status = newStatus;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isActive() {
        return !"CANCELLED".equals(status) && !"INVOICED".equals(status);
    }
}
