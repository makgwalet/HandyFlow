package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Bars one member of staff from one matter (scope MATTER) or from every matter of one client (scope CLIENT).
 * Never deleted; it ends when it is lifted, and the person it bars cannot be the one who lifts it.
 */
@Entity
@Table(name = "lp_ethical_walls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpEthicalWall {

    public static final int MIN_REASON = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 8)
    private String scope;

    @Column(name = "matter_id")
    private UUID matterId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name")
    private String userName;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "lifted_by")
    private UUID liftedBy;

    @Column(name = "lifted_by_name")
    private String liftedByName;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lift_reason")
    private String liftReason;

    public static LpEthicalWall onMatter(TenantId tenantId, UUID matterId, UUID userId, String userName, String reason, UUID by, String byName, Instant now) {
        LpEthicalWall w = base(tenantId, "MATTER", userId, userName, reason, by, byName, now);
        w.matterId = matterId;
        return w;
    }

    public static LpEthicalWall onClient(TenantId tenantId, UUID clientId, UUID userId, String userName, String reason, UUID by, String byName, Instant now) {
        LpEthicalWall w = base(tenantId, "CLIENT", userId, userName, reason, by, byName, now);
        w.clientId = clientId;
        return w;
    }

    private static LpEthicalWall base(TenantId tenantId, String scope, UUID userId, String userName, String reason, UUID by, String byName, Instant now) {
        if (userId == null) throw new IllegalArgumentException("Choose the person the wall bars");
        requireReason(reason, "Say why the wall is going up");
        LpEthicalWall w = new LpEthicalWall();
        w.tenantId = tenantId;
        w.scope = scope;
        w.userId = userId;
        w.userName = userName;
        w.reason = reason.trim();
        w.createdBy = by;
        w.createdByName = byName;
        w.createdAt = now;
        return w;
    }

    public boolean active() { return liftedAt == null; }

    /** Whether this wall bars its person from the given matter. */
    public boolean covers(UUID matter, UUID client) {
        if (!active()) return false;
        return "MATTER".equals(scope) ? matterId.equals(matter) : clientId.equals(client);
    }

    public void lift(UUID by, String byName, String why, Instant now) {
        if (!active()) throw new IllegalStateException("This wall has already been lifted");
        if (userId.equals(by)) throw new IllegalStateException("The person a wall bars cannot lift it. Someone else must");
        requireReason(why, "Say why the wall is being lifted");
        this.liftedBy = by;
        this.liftedByName = byName;
        this.liftedAt = now;
        this.liftReason = why.trim();
    }

    private static void requireReason(String text, String message) {
        if (text == null || text.trim().length() < MIN_REASON) {
            throw new IllegalArgumentException(message + " (at least " + MIN_REASON + " characters)");
        }
    }
}
