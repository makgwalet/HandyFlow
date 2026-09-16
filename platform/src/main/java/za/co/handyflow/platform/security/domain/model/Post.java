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
 * A specific guard-post location within a site — per the product
 * owner's own example: a site can have Main Gate, Loading Bay, Control
 * Room, Parking Entrance, Reception, each needing its own instructions.
 * "The guard assigned to Main Gate shouldn't necessarily see the same
 * instructions as the Control Room officer."
 */
@Entity
@Table(name = "security_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false) private UUID siteId;
    @Column(nullable = false) private String name;
    private String description;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static Post create(TenantId tenantId, UUID siteId, String name, String description) {
        Post p = new Post();
        p.tenantId = tenantId;
        p.siteId = siteId;
        p.name = name;
        p.description = description;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
