// security/domain/model/Checkpoint.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_checkpoints")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Checkpoint {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Checkpoint create(TenantId tenantId, Site site,
                                    String name, String description,
                                    int sortOrder) {
        Checkpoint c = new Checkpoint();
        c.tenantId    = tenantId;
        c.site        = site;
        c.name        = name.trim();
        c.description = description;
        // WHY UUID as QR code? Globally unique, impossible to guess,
        // contains no site info that could be exploited.
        c.qrCode      = UUID.randomUUID().toString();
        c.sortOrder   = sortOrder;
        c.active      = true;
        c.createdAt   = Instant.now();
        c.updatedAt   = Instant.now();
        return c;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}