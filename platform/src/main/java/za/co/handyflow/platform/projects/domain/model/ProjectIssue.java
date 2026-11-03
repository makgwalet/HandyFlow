package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Something that has gone wrong and needs resolving (a risk is something that might). OPEN → IN_PROGRESS → RESOLVED → CLOSED;
 * a resolved or closed issue can be reopened. Resolving needs a stated resolution.
 */
@Entity
@Table(name = "project_issues")
@Getter
@NoArgsConstructor
public class ProjectIssue {

    public static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID tenantId;
    @Column(name = "project_id", nullable = false) UUID projectId;
    @Column(name = "issue_number", nullable = false) String issueNumber;
    @Column(nullable = false) String title;
    String description;
    @Column(nullable = false) String priority = "MEDIUM";
    @Column(nullable = false) String status = "OPEN";
    @Column(name = "risk_id")        UUID riskId;
    @Column(name = "owner_id")       UUID ownerId;
    @Column(name = "owner_name")     String ownerName;
    @Column(name = "due_date")       LocalDate dueDate;
    @Column(name = "raised_by")      UUID raisedBy;
    @Column(name = "raised_by_name") String raisedByName;
    String resolution;
    @Column(name = "resolved_at")      Instant resolvedAt;
    @Column(name = "resolved_by_name") String resolvedByName;
    @Column(name = "closed_at")        Instant closedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ProjectIssue raise(UUID tenantId, UUID projectId, String number, String title, String description,
                                     String priority, UUID riskId, LocalDate dueDate, UUID raisedBy, String raisedByName) {
        ProjectIssue i = new ProjectIssue();
        i.id = UUID.randomUUID();
        i.tenantId = tenantId;
        i.projectId = projectId;
        i.issueNumber = number;
        i.title = title;
        i.description = description;
        i.priority = priority == null ? "MEDIUM" : requirePriority(priority);
        i.riskId = riskId;
        i.dueDate = dueDate;
        i.raisedBy = raisedBy;
        i.raisedByName = raisedByName;
        i.status = "OPEN";
        i.createdAt = Instant.now();
        i.updatedAt = i.createdAt;
        return i;
    }

    public boolean isOpen() { return "OPEN".equals(status) || "IN_PROGRESS".equals(status); }

    public void start() {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open issue can be started (status: " + status + ")");
        status = "IN_PROGRESS"; touch();
    }

    public void resolve(String how, String byName) {
        if (!isOpen()) throw new IllegalStateException("This issue is already " + status.toLowerCase());
        if (how == null || how.isBlank()) throw new IllegalArgumentException("Say how the issue was resolved");
        resolution = how.trim(); resolvedAt = Instant.now(); resolvedByName = byName; status = "RESOLVED"; touch();
    }

    public void close() {
        if (!"RESOLVED".equals(status)) throw new IllegalStateException("Resolve the issue before closing it");
        status = "CLOSED"; closedAt = Instant.now(); touch();
    }

    public void reopen() {
        if (isOpen()) throw new IllegalStateException("This issue is already open");
        status = "OPEN"; resolution = null; resolvedAt = null; resolvedByName = null; closedAt = null; touch();
    }

    public void edit(String title, String description, String priority, LocalDate dueDate) {
        if (title != null) { if (title.isBlank()) throw new IllegalArgumentException("Title is required"); this.title = title.trim(); }
        if (description != null) this.description = description.isBlank() ? null : description.trim();
        if (priority != null) this.priority = requirePriority(priority);
        if (dueDate != null) this.dueDate = dueDate;
        touch();
    }

    public void assign(UUID ownerId, String ownerName) { this.ownerId = ownerId; this.ownerName = ownerName; touch(); }

    private static String requirePriority(String p) {
        if (!PRIORITIES.contains(p)) throw new IllegalArgumentException("Priority must be LOW, MEDIUM, HIGH or CRITICAL");
        return p;
    }

    private void touch() { this.updatedAt = Instant.now(); }
}
