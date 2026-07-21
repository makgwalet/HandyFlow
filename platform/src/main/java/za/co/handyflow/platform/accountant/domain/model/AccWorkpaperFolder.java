package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Closes the accountant module audit's "larger workpaper system" gap.
 * Hierarchical (parent_id self-reference), scoped to one engagement
 * year per folder — a real audit-workpaper filing structure, not just
 * a flat document list like acc_fica_documents.
 */
@Entity(name = "AccountantWorkpaperFolder")
@Table(name = "acc_workpaper_folders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccWorkpaperFolder {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "engagement_year", nullable = false) private int engagementYear;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Column(name = "folder_type", length = 20) private String folderType;
    @Column(name = "sort_order", nullable = false) private int sortOrder = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AccWorkpaperFolder create(UUID tenantId, UUID clientId, UUID parentId,
                                            int engagementYear, String name, String folderType, int sortOrder) {
        AccWorkpaperFolder f = new AccWorkpaperFolder();
        f.tenantId       = tenantId;
        f.clientId       = clientId;
        f.parentId       = parentId;
        f.engagementYear = engagementYear;
        f.name           = name;
        f.folderType     = folderType;
        f.sortOrder      = sortOrder;
        f.createdAt      = Instant.now();
        return f;
    }
}