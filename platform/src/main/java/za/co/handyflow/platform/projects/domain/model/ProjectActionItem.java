package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One thing someone has to do, by a date, to deal with a risk or an issue. OPEN → DONE (or CANCELLED); can be reopened. */
@Entity
@Table(name = "project_action_items")
@Getter
@NoArgsConstructor
public class ProjectActionItem {

    @Id UUID id;
    @Column(name = "tenant_id",   nullable = false) UUID tenantId;
    @Column(name = "project_id",  nullable = false) UUID projectId;
    @Column(name = "parent_type", nullable = false) String parentType;   // RISK | ISSUE
    @Column(name = "parent_id",   nullable = false) UUID parentId;
    @Column(nullable = false) String title;
    @Column(name = "owner_id")   UUID ownerId;
    @Column(name = "owner_name") String ownerName;
    @Column(name = "due_date")   LocalDate dueDate;
    @Column(nullable = false) String status = "OPEN";
    @Column(name = "done_at")      Instant doneAt;
    @Column(name = "done_by_name") String doneByName;
    @Column(name = "created_by")      UUID createdBy;
    @Column(name = "created_by_name") String createdByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ProjectActionItem create(UUID tenantId, UUID projectId, String parentType, UUID parentId, String title,
                                           UUID ownerId, String ownerName, LocalDate dueDate, UUID by, String byName) {
        if (!"RISK".equals(parentType) && !"ISSUE".equals(parentType)) throw new IllegalArgumentException("An action belongs to a RISK or an ISSUE");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Say what has to be done");
        ProjectActionItem a = new ProjectActionItem();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId; a.projectId = projectId; a.parentType = parentType; a.parentId = parentId;
        a.title = title.trim(); a.ownerId = ownerId; a.ownerName = ownerName; a.dueDate = dueDate;
        a.status = "OPEN"; a.createdBy = by; a.createdByName = byName;
        a.createdAt = Instant.now(); a.updatedAt = a.createdAt;
        return a;
    }

    public boolean isOpen() { return "OPEN".equals(status); }

    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate != null && dueDate.isBefore(today); }

    public void markDone(String byName) {
        if (!isOpen()) throw new IllegalStateException("This action is already " + status.toLowerCase());
        status = "DONE"; doneAt = Instant.now(); doneByName = byName; touch();
    }

    public void cancel() {
        if (!isOpen()) throw new IllegalStateException("This action is already " + status.toLowerCase());
        status = "CANCELLED"; touch();
    }

    public void reopen() {
        if (isOpen()) throw new IllegalStateException("This action is already open");
        status = "OPEN"; doneAt = null; doneByName = null; touch();
    }

    public void edit(String title, LocalDate dueDate) {
        if (title != null) { if (title.isBlank()) throw new IllegalArgumentException("Title is required"); this.title = title.trim(); }
        if (dueDate != null) this.dueDate = dueDate;
        touch();
    }

    public void assign(UUID ownerId, String ownerName) { this.ownerId = ownerId; this.ownerName = ownerName; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
