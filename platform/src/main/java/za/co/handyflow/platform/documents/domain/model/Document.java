package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A document with an owner. Its files are {@link DocumentVersion}s. Archived, never deleted. */
@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(nullable = false)
    private String status = ACTIVE;

    @Column(name = "archived_reason")
    private String archivedReason;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static Document create(TenantId tenantId, String ownerType, UUID ownerId, String category, String title, String description,
                                  LocalDate expiresOn, UUID by) {
        Document d = new Document();
        d.tenantId = tenantId;
        d.ownerType = ownerType;
        d.ownerId = ownerId;
        d.category = category;
        d.title = DocumentRules.title(title);
        d.description = DocumentRules.optional(description, 500, "description");
        d.expiresOn = expiresOn;
        d.createdBy = by;
        return d;
    }

    public boolean isActive() { return ACTIVE.equals(status); }

    public void details(String title, String description, LocalDate expiresOn) {
        requireActive();
        this.title = DocumentRules.title(title);
        this.description = DocumentRules.optional(description, 500, "description");
        this.expiresOn = expiresOn;
        this.updatedAt = Instant.now();
    }

    /** A new version, optionally with a renewed expiry date (null keeps the date). */
    public int nextVersion(LocalDate renewedExpiry) {
        requireActive();
        this.currentVersion++;
        if (renewedExpiry != null) this.expiresOn = renewedExpiry;
        this.updatedAt = Instant.now();
        return currentVersion;
    }

    public void archive(String reason, UUID by) {
        requireActive();
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw new IllegalArgumentException("Say why this document is being archived (at least 5 characters).");
        if (r.length() > 300) throw new IllegalArgumentException("Keep the reason to 300 characters.");
        this.status = ARCHIVED;
        this.archivedReason = r;
        this.archivedBy = by;
        this.archivedAt = Instant.now();
        this.updatedAt = this.archivedAt;
    }

    private void requireActive() { if (!isActive()) throw new IllegalStateException("This document is archived and cannot be changed."); }
}
