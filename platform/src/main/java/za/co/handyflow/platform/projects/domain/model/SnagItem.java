package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.StringListConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "snag_items")
@Getter
@NoArgsConstructor
public class SnagItem {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID    tenantId;
    @Column(name = "project_id", nullable = false) UUID    projectId;
    @Column(name = "task_id")                       UUID    taskId;
    @Column(name = "snag_number", nullable = false, length = 20) String snagNumber;
    @Column(nullable = false, length = 300) String title;
    String description;
    @Column(length = 200) String location;
    // severity: LOW | MEDIUM | HIGH | CRITICAL
    @Column(nullable = false, length = 10) String severity = "MEDIUM";
    // status: OPEN | IN_PROGRESS | RESOLVED | REJECTED
    @Column(nullable = false, length = 20) String status   = "OPEN";
    @Column(name = "assigned_to")      UUID   assignedTo;
    @Column(name = "assigned_to_name") String assignedToName;
    @Column(name = "due_date")         LocalDate dueDate;
    // photo_urls is a PostgreSQL TEXT[] column.
    // StringListConverter serialises List<String> to/from the PostgreSQL array format.
    // columnDefinition omitted deliberately — Hibernate validates against VARCHAR (the
    // converter's JDBC type) which conflicts with _text (ARRAY). The column works
    // correctly at runtime; only schema-validation trips over the type mismatch.
    @Convert(converter = StringListConverter.class)
    @Column(name = "photo_urls")
    List<String> photoUrls;
    @Column(name = "resolved_at") Instant resolvedAt;
    @Column(name = "resolved_by") UUID    resolvedBy;
    @Column(name = "created_by")  UUID    createdBy;
    @Column(name = "created_at",  nullable = false) Instant createdAt;
    @Column(name = "updated_at",  nullable = false) Instant updatedAt;

    public static SnagItem create(UUID tenantId, UUID projectId, UUID taskId,
                                  String snagNumber, String title, String description,
                                  String location, String severity,
                                  UUID assignedTo, String assignedToName,
                                  LocalDate dueDate, UUID createdBy) {
        SnagItem s       = new SnagItem();
        s.id             = UUID.randomUUID();
        s.tenantId       = tenantId;
        s.projectId      = projectId;
        s.taskId         = taskId;
        s.snagNumber     = snagNumber;
        s.title          = title;
        s.description    = description;
        s.location       = location;
        s.severity       = severity != null ? severity : "MEDIUM";
        s.assignedTo     = assignedTo;
        s.assignedToName = assignedToName;
        s.dueDate        = dueDate;
        s.status         = "OPEN";
        s.createdBy      = createdBy;
        s.createdAt      = Instant.now();
        s.updatedAt      = Instant.now();
        return s;
    }

    public void startWork() { this.status = "IN_PROGRESS"; touch(); }

    public void resolve(UUID resolvedBy) {
        this.status     = "RESOLVED";
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        touch();
    }

    public void reject() { this.status = "REJECTED"; touch(); }

    public void addPhoto(String url) {
        if (this.photoUrls == null) this.photoUrls = new java.util.ArrayList<>();
        this.photoUrls.add(url);
        touch();
    }

    public void setSeverity(String v)       { this.severity = v; touch(); }
    public void setAssignedTo(UUID v)       { this.assignedTo = v; touch(); }
    public void setAssignedToName(String v) { this.assignedToName = v; touch(); }
    public void setDueDate(LocalDate v)     { this.dueDate = v; touch(); }
    public void setDescription(String v)    { this.description = v; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}