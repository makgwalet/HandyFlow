package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A simple URL+name attachment on a post order (a site map, an
 * evacuation diagram) — matching the attachmentUrl/attachmentName
 * pattern already used elsewhere in this codebase (e.g. ApBill) rather
 * than a new multi-file subsystem, since a post order's attachments are
 * typically one or two files, not a document library.
 */
@Entity
@Table(name = "security_post_order_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostOrderAttachment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "post_order_id", nullable = false) private UUID postOrderId;
    @Column(name = "file_url", nullable = false) private String fileUrl;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static PostOrderAttachment create(TenantId tenantId, UUID postOrderId, String fileUrl, String fileName) {
        PostOrderAttachment a = new PostOrderAttachment();
        a.tenantId = tenantId;
        a.postOrderId = postOrderId;
        a.fileUrl = fileUrl;
        a.fileName = fileName;
        a.createdAt = Instant.now();
        return a;
    }
}
