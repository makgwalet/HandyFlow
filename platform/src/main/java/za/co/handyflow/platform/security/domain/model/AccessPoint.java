// security/domain/model/AccessPoint.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * AccessPoint — a manned gate/entrance at a Site where a guard logs
 * visitors, contractors, deliveries, and vehicles in and out.
 * <p>
 * Deliberately NOT a reuse of Checkpoint — see the plan doc's own §3 for
 * the full reasoning (Checkpoint is stateless, self-scan, QR/NFC/BLE-
 * verified; AccessPoint is stateful, guard-logs-third-parties, no
 * verification method of its own). Also deliberately standalone rather
 * than waiting on a general Post entity — Post doesn't exist yet and
 * isn't small work on its own; "does a Post generalize this" is an
 * explicit question for whenever Post gets designed, not a blocker here.
 */
@Entity
@Table(name = "security_access_points")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AccessPoint {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static AccessPoint create(TenantId tenantId, UUID siteId, String name, String description) {
        AccessPoint a  = new AccessPoint();
        a.tenantId     = tenantId;
        a.siteId       = siteId;
        a.name         = name;
        a.description  = description;
        a.active       = true;
        a.createdAt    = Instant.now();
        a.updatedAt    = Instant.now();
        return a;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void update(String name, String description) {
        this.name        = name;
        this.description = description;
        this.updatedAt   = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active    = true;
        this.updatedAt = Instant.now();
    }
}