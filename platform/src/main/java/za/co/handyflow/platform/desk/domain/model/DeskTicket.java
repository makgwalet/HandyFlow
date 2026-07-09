package za.co.handyflow.platform.desk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desk_tickets")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeskTicket {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "ticket_number", nullable = false) private String ticketNumber;
    @Column(nullable = false)                          private String channel = "HELPDESK";

    // Requester
    @Column(name = "requester_name",  nullable = false) private String requesterName;
    @Column(name = "requester_email")                   private String requesterEmail;
    @Column(name = "requester_phone")                   private String requesterPhone;
    @Column(name = "customer_id")                       private UUID   customerId;

    // Content
    @Column(nullable = false) private String subject;
    @Column(nullable = false) private String description;
    @Column(name = "category_id") private UUID categoryId;

    // Priority + status
    @Column(nullable = false) private String priority = "NORMAL";
    @Column(nullable = false) private String status   = "OPEN";

    // Assignment
    @Column(name = "assigned_to") private UUID assignedTo;

    // SLA
    @Column(name = "first_response_at") private Instant firstResponseAt;
    @Column(name = "resolved_at")       private Instant resolvedAt;
    @Column(name = "closed_at")         private Instant closedAt;
    @Column(name = "sla_breached")      private boolean slaBreached = false;
    @Column(name = "due_at")            private Instant dueAt;

    // NEW: backs the SLA-pause fix. V36's own migration comment says "SLA
    // clock pauses on WAITING_ON_CUSTOMER" — the timestamps existed to
    // support that, but nothing anywhere ever actually paused anything.
    // dueAt was a fixed deadline set once at creation and never adjusted,
    // meaning a ticket waiting five days on a slow customer would show as
    // SLA-breached through no fault of the support team at all.
    @Column(name = "paused_at") private Instant pausedAt;

    // Public portal
    @Column(name = "public_token", unique = true) private String publicToken;

    private String notes;

    @Column(name = "created_by") private UUID    createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    @Version private Long version;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static DeskTicket create(TenantId tenantId, String ticketNumber,
                                    String channel, String requesterName,
                                    String requesterEmail, String requesterPhone,
                                    UUID customerId, String subject, String description,
                                    UUID categoryId, String priority,
                                    Instant dueAt, UUID createdBy) {
        DeskTicket t      = new DeskTicket();
        t.tenantId        = tenantId;
        t.ticketNumber    = ticketNumber;
        t.channel         = channel != null ? channel : "HELPDESK";
        t.requesterName   = requesterName;
        t.requesterEmail  = requesterEmail;
        t.requesterPhone  = requesterPhone;
        t.customerId      = customerId;
        t.subject         = subject;
        t.description     = description;
        t.categoryId      = categoryId;
        t.priority        = priority != null ? priority : "NORMAL";
        t.dueAt           = dueAt;
        t.createdBy       = createdBy;
        t.status          = "OPEN";
        t.publicToken     = UUID.randomUUID().toString().replace("-","")
                + UUID.randomUUID().toString().replace("-","");
        t.createdAt       = Instant.now();
        t.updatedAt       = Instant.now();
        return t;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void assign(UUID userId) {
        this.assignedTo = userId;
        if ("OPEN".equals(status)) this.status = "IN_PROGRESS";
        touch();
    }

    public void startProgress() {
        resumeIfPaused();
        transition("IN_PROGRESS");
    }

    // FIX: these previously did nothing but flip the status string — the
    // SLA deadline kept counting down the whole time a ticket sat waiting
    // on someone else to respond. Now actually records when the pause
    // began; see resumeIfPaused() for the other half.
    public void waitOnCustomer() {
        if (this.pausedAt == null) this.pausedAt = Instant.now();
        transition("WAITING_ON_CUSTOMER");
    }

    public void waitOnThirdParty() {
        if (this.pausedAt == null) this.pausedAt = Instant.now();
        transition("WAITING_ON_THIRD_PARTY");
    }

    public void resolve() {
        resumeIfPaused();
        this.status     = "RESOLVED";
        this.resolvedAt = Instant.now();
        touch();
    }

    public void close() {
        this.status   = "CLOSED";
        this.closedAt = Instant.now();
        touch();
    }

    public void reopen() {
        this.status     = "OPEN";
        this.resolvedAt = null;
        touch();
    }

    public void recordFirstResponse() {
        if (this.firstResponseAt == null) {
            this.firstResponseAt = Instant.now();
            touch();
        }
    }

    public void markSlaBreached() {
        this.slaBreached = true;
        touch();
    }

    public void updateDetails(String subject, String description,
                              UUID categoryId, String priority, String notes) {
        if (subject     != null) this.subject     = subject;
        if (description != null) this.description = description;
        if (categoryId  != null) this.categoryId  = categoryId;
        if (priority    != null) this.priority    = priority;
        if (notes       != null) this.notes       = notes;
        touch();
    }

    private void transition(String newStatus) { this.status = newStatus; touch(); }
    private void touch() { this.updatedAt = Instant.now(); }

    // NEW: the other half of the SLA-pause fix. Extends dueAt by however
    // long the ticket was actually paused, so the deadline reflects real
    // support-team-controlled time rather than wall-clock time that
    // includes waiting on someone else. A no-op when pausedAt is null
    // (e.g. starting fresh from OPEN, never having been paused at all).
    private void resumeIfPaused() {
        if (pausedAt != null && dueAt != null) {
            Duration pauseDuration = Duration.between(pausedAt, Instant.now());
            dueAt = dueAt.plus(pauseDuration);
        }
        pausedAt = null;
    }

    public boolean isOpen()     { return !"CLOSED".equals(status) && !"RESOLVED".equals(status); }
    public boolean isResolved() { return "RESOLVED".equals(status) || "CLOSED".equals(status); }
}