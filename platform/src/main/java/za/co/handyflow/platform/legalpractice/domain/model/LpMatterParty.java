package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone other than the client who is involved in a matter (the other side, a co-party, a related party, a witness).
 * Recorded when a matter is opened from a conflict check, and searched by every later check, which is how the firm
 * notices that a new client is the other side of an old matter. Never changed or deleted.
 */
@Entity
@Table(name = "lp_matter_parties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpMatterParty {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false)
    private UUID matterId;

    /** The firm's client on that matter, kept here so a search can skip the client's own earlier matters. */
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "id_number", length = 100)
    private String idNumber;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public static LpMatterParty of(TenantId tenantId, UUID matterId, UUID clientId, String name, String idNumber, String role) {
        LpMatterParty p = new LpMatterParty();
        p.tenantId = tenantId;
        p.matterId = matterId;
        p.clientId = clientId;
        p.name = name;
        p.idNumber = idNumber;
        p.role = role;
        p.addedAt = Instant.now();
        return p;
    }
}
