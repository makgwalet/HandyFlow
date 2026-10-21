package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A picture or document attached to an item. The bytes live in the shared file store; this row keeps the opaque key. */
@Getter
@Entity
@Table(name = "catalogue_item_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueItemMedia {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private MediaRules.Kind kind;

    @Column(length = 120)
    private String title;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(name = "file_name", nullable = false, length = 200, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "uploaded_by", updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static CatalogueItemMedia create(TenantId tenantId, UUID itemId, MediaRules.Kind kind, String title, String altText,
                                            String fileName, String contentType, long sizeBytes, String storageKey,
                                            boolean primary, int sortOrder, UUID by) {
        var m = new CatalogueItemMedia();
        m.tenantId = tenantId;
        m.itemId = itemId;
        m.kind = kind;
        m.title = title;
        m.altText = altText;
        m.fileName = fileName;
        m.contentType = contentType;
        m.sizeBytes = sizeBytes;
        m.storageKey = storageKey;
        m.primary = primary && kind == MediaRules.Kind.IMAGE;
        m.sortOrder = sortOrder;
        m.uploadedBy = by;
        return m;
    }

    public void describe(String title, String altText) { this.title = title; this.altText = altText; }

    public void setPrimary(boolean primary) {
        if (primary && kind != MediaRules.Kind.IMAGE) throw new IllegalArgumentException("Only a picture can be the main picture.");
        this.primary = primary;
    }

    public void moveTo(int sortOrder) { this.sortOrder = sortOrder; }

    public void softDelete() { this.deletedAt = Instant.now(); this.primary = false; }

    public boolean isDeleted() { return deletedAt != null; }
}
