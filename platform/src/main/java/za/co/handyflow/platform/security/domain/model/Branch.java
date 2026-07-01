// security/domain/model/Branch.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_branches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Branch {
    @Id private UUID id = UUID.randomUUID();
    @Embedded @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 100) private String region;
    @Column private String description;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static Branch create(TenantId tenantId, String name, String region, String description) {
        Branch b = new Branch();
        b.tenantId = tenantId; b.name = name.strip(); b.region = region;
        b.description = description; b.active = true;
        b.createdAt = Instant.now(); b.updatedAt = Instant.now();
        return b;
    }
    public void update(String name, String region, String description) {
        this.name = name.strip(); this.region = region; this.description = description;
        this.updatedAt = Instant.now();
    }
    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
}