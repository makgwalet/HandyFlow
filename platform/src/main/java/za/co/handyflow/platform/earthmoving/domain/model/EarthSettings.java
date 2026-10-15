package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per company: the settings that belong to the Earthmoving module. */
@Entity
@Table(name = "earthmoving_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthSettings {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Hire value from which a signed contract is expected; null means never. */
    @Column(name = "contract_threshold", precision = 14, scale = 2)
    private BigDecimal contractThreshold;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static EarthSettings forTenant(TenantId tenantId) {
        EarthSettings s = new EarthSettings();
        s.tenantId = tenantId.getValue();
        return s;
    }

    public void setContractThreshold(BigDecimal threshold, UUID actor) {
        this.contractThreshold = ContractRules.checkThreshold(threshold);
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
