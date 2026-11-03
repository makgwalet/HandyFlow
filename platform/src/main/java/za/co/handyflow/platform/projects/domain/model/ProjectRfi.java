package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import za.co.handyflow.platform.projects.domain.enums.RfiStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Request For Information.
 *
 * CHUNK 7 CHANGES
 * ───────────────
 * - Lifecycle follows RfiStatus; every change is checked there. The class-level @Setter is gone,
 *   so status can no longer be set directly — only through the domain methods below.
 * - assignedTo*: who has to answer. Only the assignee (or a PM_ADMIN) may respond once assigned.
 * - `response` still holds the LATEST answer; every answer, clarification and reopening is kept
 *   in project_rfi_events, so a later answer never erases an earlier one.
 * - requestClarification(): RESPONDED → SUBMITTED, back to the responder.
 * - impactsSchedule / impactsCost / impactTaskId / impactNotes: downstream impact flagged by the
 *   answer. Closing is governed by RfiRules.closureBlocker().
 * - escalationLevel: 0–3, raised by the nightly overdue sweep through an atomic conditional UPDATE
 *   (ProjectRfiRepository.raiseEscalationLevel), so concurrent instances can't both escalate.
 */
@Entity
@Table(name = "project_rfis", schema = "public")
@Getter
@NoArgsConstructor
public class ProjectRfi {

    @Id @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id",   nullable = false) private UUID tenantId;
    @Column(name = "project_id",  nullable = false) private UUID projectId;

    @Column(name = "rfi_number",  nullable = false, length = 20) private String rfiNumber;   // RFI-001 …

    @Column(nullable = false, length = 500) private String title;
    @Column(columnDefinition = "TEXT")      private String description;
    @Column(length = 50)                    private String category;   // DESIGN|SITE|MATERIALS|SAFETY|SPECIFICATION|OTHER

    // ── Request side ──
    @Column(name = "requested_by",   length = 255) private String requestedBy;
    @Column(name = "requested_by_id")              private UUID requestedById;
    @Column(name = "requested_date", nullable = false) private LocalDate requestedDate = LocalDate.now();
    @Column(name = "due_date")                     private LocalDate dueDate;

    // ── Assignment ──
    @Column(name = "assigned_to_id")   private UUID assignedToId;
    @Column(name = "assigned_to_name") private String assignedToName;
    @Column(name = "assigned_at")      private LocalDateTime assignedAt;

    // ── Response side (latest answer; full history in project_rfi_events) ──
    @Column(name = "responded_by",    length = 255) private String respondedBy;
    @Column(name = "responded_by_id")               private UUID respondedById;
    @Column(name = "responded_date")                private LocalDate respondedDate;
    @Column(columnDefinition = "TEXT")              private String response;
    @Column(name = "clarification_count", nullable = false) private int clarificationCount = 0;

    // ── Impact ──
    @Column(name = "impact_task_id")                      private UUID impactTaskId;
    @Column(name = "impacts_schedule", nullable = false)  private boolean impactsSchedule = false;
    @Column(name = "impacts_cost",     nullable = false)  private boolean impactsCost = false;
    @Column(name = "impact_notes", columnDefinition = "TEXT") private String impactNotes;
    @Column(name = "no_change_note", columnDefinition = "TEXT") private String noChangeNote;
    @Column(name = "change_order_id")                     private UUID changeOrderId;

    // ── Lifecycle ──
    @Column(nullable = false, length = 30) private String status = "DRAFT";
    @Column(name = "escalation_level", nullable = false) private int escalationLevel = 0;
    @Column(name = "closed_at")            private LocalDateTime closedAt;
    @Column(name = "cancelled_at")         private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason", columnDefinition = "TEXT") private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)                    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ProjectRfi create(UUID tenantId, UUID projectId, String rfiNumber, String title,
                                    String description, String category, String requestedBy,
                                    UUID requestedById, LocalDate requestedDate, LocalDate dueDate) {
        ProjectRfi r = new ProjectRfi();
        r.tenantId      = tenantId;
        r.projectId     = projectId;
        r.rfiNumber     = rfiNumber;
        r.title         = title;
        r.description   = description;
        r.category      = category;
        r.requestedBy   = requestedBy;
        r.requestedById = requestedById;
        r.requestedDate = requestedDate != null ? requestedDate : LocalDate.now();
        r.dueDate       = dueDate;
        return r;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public void submit() { transition(RfiStatus.SUBMITTED); }

    public void respond(String respondedBy, UUID respondedById, String response) {
        transition(RfiStatus.RESPONDED);
        this.respondedBy   = respondedBy;
        this.respondedById = respondedById;
        this.respondedDate = LocalDate.now();
        this.response      = response;
    }

    /** RESPONDED → SUBMITTED. The latest answer stays visible until a new one replaces it. */
    public void requestClarification() {
        transition(RfiStatus.SUBMITTED);
        this.clarificationCount++;
        this.escalationLevel = 0;         // a fresh question: escalation starts again if it runs late
    }

    public void close(String noChangeNote) {
        transition(RfiStatus.CLOSED);
        this.closedAt = LocalDateTime.now();
        if (noChangeNote != null && !noChangeNote.isBlank()) this.noChangeNote = noChangeNote.trim();
    }

    /** CLOSED → RESPONDED. */
    public void reopen() {
        transition(RfiStatus.RESPONDED);
        this.closedAt = null;
    }

    public void cancel(String reason) {
        transition(RfiStatus.CANCELLED);
        this.cancelledAt        = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    public void assign(UUID assigneeId, String assigneeName) {
        if (statusEnum().isTerminal()) throw new IllegalStateException("A cancelled RFI cannot be assigned");
        this.assignedToId   = assigneeId;
        this.assignedToName = assigneeName;
        this.assignedAt     = LocalDateTime.now();
    }

    public void updateImpact(UUID taskId, boolean schedule, boolean cost, String notes) {
        if (statusEnum().isTerminal()) throw new IllegalStateException("A cancelled RFI's impact can no longer be changed");
        this.impactTaskId     = taskId;
        this.impactsSchedule  = schedule;
        this.impactsCost      = cost;
        this.impactNotes      = notes;
    }

    /** Re-linking is allowed: the wrong change order may have been picked first. */
    public void linkChangeOrder(UUID changeOrderId) { this.changeOrderId = changeOrderId; }

    public RfiStatus statusEnum() { return RfiStatus.valueOf(status); }

    /** An RFI stops being "overdue" once answered, closed or cancelled. */
    public boolean isOverdue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate)
                && ("DRAFT".equals(status) || "SUBMITTED".equals(status));
    }

    /** Negative once overdue. */
    public int daysUntilDue() {
        if (dueDate == null) return 0;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }

    private void transition(RfiStatus target) {
        RfiStatus current = RfiStatus.valueOf(status);
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException("A " + current + " RFI cannot become " + target);
        }
        this.status = target.name();
    }
}