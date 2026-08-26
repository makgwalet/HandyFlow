package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_rfis", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ProjectRfi {

    @Id @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id",   nullable = false)
    private UUID tenantId;

    @Column(name = "project_id",  nullable = false)
    private UUID projectId;

    // ── Identifier ───────────────────────────────────────────────────────────
    @Column(name = "rfi_number",  nullable = false, length = 20)
    private String rfiNumber;                       // RFI-001, RFI-002 …

    // ── Content ──────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;                        // DESIGN|SITE|MATERIALS|SAFETY|SPECIFICATION|OTHER

    // ── Request side ─────────────────────────────────────────────────────────
    @Column(name = "requested_by",     length = 255)
    private String requestedBy;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "requested_date",   nullable = false)
    private LocalDate requestedDate = LocalDate.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    // ── Response side ────────────────────────────────────────────────────────
    @Column(name = "responded_by",     length = 255)
    private String respondedBy;

    @Column(name = "responded_by_id")
    private UUID respondedById;

    @Column(name = "responded_date")
    private LocalDate respondedDate;

    @Column(columnDefinition = "TEXT")
    private String response;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";               // DRAFT|SUBMITTED|RESPONDED|CLOSED|CANCELLED

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    // FIX: backlog 6.3 — nullable link to a Change Order this RFI's
    // answer resulted in. No FK constraint added deliberately — same
    // "loose reference, resolved by the service layer" convention this
    // module already uses for cross-entity links, not something new
    // introduced here.
    @Column(name = "change_order_id")
    private UUID changeOrderId;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ── Domain methods ────────────────────────────────────────────────────────

    public void submit() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT RFIs can be submitted");
        this.status = "SUBMITTED";
    }

    public void respond(String respondedBy, UUID respondedById, String response) {
        if (!"SUBMITTED".equals(status)) throw new IllegalStateException("Only SUBMITTED RFIs can be responded to");
        this.status        = "RESPONDED";
        this.respondedBy   = respondedBy;
        this.respondedById = respondedById;
        this.respondedDate = LocalDate.now();
        this.response      = response;
    }

    public void close() {
        if (!"RESPONDED".equals(status)) throw new IllegalStateException("Only RESPONDED RFIs can be closed");
        this.status   = "CLOSED";
        this.closedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        if ("CLOSED".equals(status) || "CANCELLED".equals(status))
            throw new IllegalStateException("Cannot cancel a " + status + " RFI");
        this.status               = "CANCELLED";
        this.cancelledAt          = LocalDateTime.now();
        this.cancellationReason   = reason;
    }

    /**
     * FIX: backlog 6.3 — links this RFI to the Change Order its answer
     * resulted in. Deliberately allows re-linking (no "already linked"
     * guard) — an RFI's answer might reasonably need correcting to a
     * different Change Order if the wrong one was picked initially, and
     * blocking that would just push people to edit the database
     * directly instead.
     */
    public void linkChangeOrder(UUID changeOrderId) {
        this.changeOrderId = changeOrderId;
    }

    /**
     * FIX: backlog 6.3 — same "isOverdue()" convention already
     * established on ApBill. An RFI stops being meaningfully "overdue"
     * once it's actually been answered (RESPONDED/CLOSED) or is no
     * longer live (CANCELLED) — even if the closing/filing paperwork
     * hasn't caught up yet, the thing a due date is meant to track (a
     * timely answer) has already happened.
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate)
                && ("DRAFT".equals(status) || "SUBMITTED".equals(status));
    }

    /**
     * FIX: backlog 6.3. Negative once overdue, matching ApBill.
     * daysUntilDue()'s own sign convention — callers already familiar
     * with that pattern get the same meaning here for free.
     */
    public int daysUntilDue() {
        if (dueDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }
}