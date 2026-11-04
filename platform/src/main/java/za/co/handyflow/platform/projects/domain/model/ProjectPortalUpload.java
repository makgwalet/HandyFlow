package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A file a client sent. It never enters the document register on its own: staff accept it (it becomes a register entry) or reject it. */
@Entity
@Table(name = "project_portal_uploads")
@Getter
@NoArgsConstructor
public class ProjectPortalUpload {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "project_id", nullable = false) UUID projectId;
    @Column(name = "grant_id", nullable = false) UUID grantId;
    @Column(name = "evidence_id", nullable = false) UUID evidenceId;
    @Column(name = "file_name", nullable = false) String fileName;
    @Column(name = "size_bytes", nullable = false) long sizeBytes;
    @Column(nullable = false) String sha256;
    String note;
    @Column(name = "uploaded_by_name", nullable = false) String uploadedByName;
    @Column(name = "uploaded_by_email", nullable = false) String uploadedByEmail;
    @Column(nullable = false) String status = "PENDING";
    String reason;
    @Column(name = "document_id") UUID documentId;
    @Column(name = "decided_by") UUID decidedBy;
    @Column(name = "decided_by_name") String decidedByName;
    @Column(name = "decided_at") Instant decidedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ProjectPortalUpload received(UUID id, UUID tenantId, UUID projectId, UUID grantId, UUID evidenceId, String fileName, long size,
                                               String sha256, String note, String byName, String byEmail) {
        ProjectPortalUpload u = new ProjectPortalUpload();
        u.id = id; u.tenantId = tenantId; u.projectId = projectId; u.grantId = grantId; u.evidenceId = evidenceId;
        u.fileName = fileName; u.sizeBytes = size; u.sha256 = sha256; u.note = note == null || note.isBlank() ? null : note.trim();
        u.uploadedByName = byName; u.uploadedByEmail = byEmail; u.createdAt = Instant.now();
        return u;
    }

    public boolean isPending() { return "PENDING".equals(status); }

    public void accept(UUID documentId, UUID by, String byName) {
        if (!isPending()) throw new IllegalStateException("This file has already been " + status.toLowerCase());
        this.status = "ACCEPTED"; this.documentId = documentId; this.decidedBy = by; this.decidedByName = byName; this.decidedAt = Instant.now();
    }

    public void reject(String reason, UUID by, String byName) {
        if (!isPending()) throw new IllegalStateException("This file has already been " + status.toLowerCase());
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Say why the file is not accepted");
        this.status = "REJECTED"; this.reason = reason.trim(); this.decidedBy = by; this.decidedByName = byName; this.decidedAt = Instant.now();
    }
}
