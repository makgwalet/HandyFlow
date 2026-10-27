package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Grants a staff user access to one client portfolio. A user with at least one row is restricted to
 * exactly those clients; a user with none is unrestricted (backward compatible). {@code userId} is an
 * unvalidated reference to a system user, like {@code FmTechnician.linkedUserId}.
 */
@Entity
@Table(name = "fm_client_access")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmClientAccess {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FmClientAccess grant(TenantId tenantId, UUID userId, UUID clientId, UUID grantedBy) {
        FmClientAccess a = new FmClientAccess();
        a.tenantId = tenantId;
        a.userId = userId;
        a.clientId = clientId;
        a.grantedBy = grantedBy;
        a.createdAt = Instant.now();
        return a;
    }
}