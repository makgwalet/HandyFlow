package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Direct structural mirror of AccWorkpaperFolder (Accountant module) —
 * hierarchical (parentId self-reference), same folder_type-as-tag
 * approach, just scoped to one audit engagement rather than a client +
 * recurring engagement year, since an Internal Audit workpaper folder
 * belongs to exactly one specific engagement.
 */
@Entity(name = "AuditWorkpaperFolder")
@Table(name = "audit_workpaper_folders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditWorkpaperFolder {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "folder_type", length = 20) private String folderType; // PLANNING | FIELDWORK | SAMPLING | FINDINGS | REPORTING | GENERAL
    @Column(name = "sort_order", nullable = false) private int sortOrder = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AuditWorkpaperFolder create(UUID tenantId, UUID engagementId, UUID parentId,
                                              String name, String folderType, int sortOrder) {
        AuditWorkpaperFolder f = new AuditWorkpaperFolder();
        f.tenantId = tenantId;
        f.engagementId = engagementId;
        f.parentId = parentId;
        f.name = name;
        f.folderType = folderType;
        f.sortOrder = sortOrder;
        f.createdAt = Instant.now();
        return f;
    }
}
