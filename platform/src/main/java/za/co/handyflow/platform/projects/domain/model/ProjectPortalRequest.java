package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Something a client sent in: a QUESTION, a CHANGE request, or a NEW_PROJECT request. OPEN until staff accept, answer or decline it. */
@Entity
@Table(name = "project_portal_requests")
@Getter
@NoArgsConstructor
public class ProjectPortalRequest {

    public static final Set<String> KINDS = Set.of("QUESTION", "CHANGE", "NEW_PROJECT");

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "project_id") UUID projectId;
    @Column(name = "grant_id", nullable = false) UUID grantId;
    @Column(name = "request_number", nullable = false) String requestNumber;
    @Column(nullable = false) String kind;
    @Column(nullable = false) String title;
    String description;
    @Column(name = "needed_by") LocalDate neededBy;
    @Column(name = "raised_by_name", nullable = false) String raisedByName;
    @Column(name = "raised_by_email", nullable = false) String raisedByEmail;
    @Column(nullable = false) String status = "OPEN";
    String response;
    @Column(name = "decided_by") UUID decidedBy;
    @Column(name = "decided_by_name") String decidedByName;
    @Column(name = "decided_at") Instant decidedAt;
    @Column(name = "linked_type") String linkedType;
    @Column(name = "linked_id") UUID linkedId;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ProjectPortalRequest raise(UUID tenantId, UUID projectId, UUID grantId, String number, String kind, String title,
                                             String description, LocalDate neededBy, String byName, String byEmail) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown request type");
        if ("NEW_PROJECT".equals(kind) != (projectId == null)) throw new IllegalArgumentException("A new-project request has no project; other requests need one");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Give the request a title");
        ProjectPortalRequest r = new ProjectPortalRequest();
        r.id = UUID.randomUUID(); r.tenantId = tenantId; r.projectId = projectId; r.grantId = grantId; r.requestNumber = number;
        r.kind = kind; r.title = title.trim(); r.description = description == null || description.isBlank() ? null : description.trim();
        r.neededBy = neededBy; r.raisedByName = byName; r.raisedByEmail = byEmail; r.createdAt = Instant.now();
        return r;
    }

    public boolean isOpen() { return "OPEN".equals(status); }

    /** ACCEPTED (with what it became), ANSWERED or DECLINED. The response is shown to the client. */
    public void decide(String outcome, String response, UUID by, String byName, String linkedType, UUID linkedId) {
        if (!isOpen()) throw new IllegalStateException("This request has already been " + status.toLowerCase());
        if (!Set.of("ACCEPTED", "ANSWERED", "DECLINED").contains(outcome)) throw new IllegalArgumentException("Unknown outcome");
        if (response == null || response.isBlank()) throw new IllegalArgumentException("Write a reply for the client");
        this.status = outcome; this.response = response.trim(); this.decidedBy = by; this.decidedByName = byName; this.decidedAt = Instant.now();
        this.linkedType = linkedType; this.linkedId = linkedId;
    }
}
