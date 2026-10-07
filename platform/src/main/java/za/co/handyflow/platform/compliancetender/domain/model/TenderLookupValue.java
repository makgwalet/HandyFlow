package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One value a company added to one of its pick-lists. */
@Entity
@Table(name = "tender_lookup_values")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderLookupValue {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "list_key", nullable = false, updatable = false)
    private String listKey;

    @Column(nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    public static TenderLookupValue create(TenantId tenantId, String listKey, String value, UUID by) {
        TenderLookupValue v = new TenderLookupValue();
        v.tenantId = tenantId;
        v.listKey = listKey;
        v.value = value;
        v.createdBy = by;
        return v;
    }
}
