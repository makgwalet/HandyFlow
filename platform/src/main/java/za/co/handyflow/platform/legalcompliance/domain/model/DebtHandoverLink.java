package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Remembers which LitigationMatter was created for which debt-collection handover, so the gateway is idempotent:
 * a retried transfer for the same handover finds this row and returns the SAME matter instead of opening a second
 * one. A separate table (rather than a new column on LitigationMatter) keeps the litigation entity untouched.
 * The unique index on (tenant_id, handover_id) also settles a race between two concurrent transfers.
 */
@Entity
@Table(name = "legalcompliance_debt_handover_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtHandoverLink extends AggregateRoot<DebtHandoverLink> {

    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;

    @Column(name = "matter_id", nullable = false)
    private UUID matterId;

    public static DebtHandoverLink of(TenantId tenantId, UUID handoverId, UUID matterId) {
        DebtHandoverLink l = new DebtHandoverLink();
        l.initTenantId(tenantId);
        l.handoverId = handoverId;
        l.matterId = matterId;
        return l;
    }
}
