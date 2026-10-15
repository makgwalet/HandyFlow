package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** A rate agreed in a hire contract, for one billing basis. Charges on deployments under that contract use it before the machine's own rate. */
@Entity
@Table(name = "earthmoving_contract_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthContractRate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(nullable = false)
    private String basis;

    @Column(name = "unit_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitRate;

    /** Kilometres included before the rate applies; only for a kilometre rate. */
    @Column(name = "included_km", precision = 10, scale = 2)
    private BigDecimal includedKm;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EarthContractRate of(TenantId tenantId, UUID contractId, String basis, BigDecimal rate, BigDecimal includedKm, UUID actor) {
        EarthContractRate r = new EarthContractRate();
        r.tenantId = tenantId;
        r.contractId = contractId;
        r.basis = HireRules.basis(basis);
        r.set(rate, includedKm, actor);
        return r;
    }

    public void set(BigDecimal rate, BigDecimal includedKm, UUID actor) {
        this.unitRate = HireRules.contractRate(basis, rate);
        if (includedKm != null && !HireRules.KILOMETRE.equals(basis)) throw new IllegalArgumentException("Included kilometres belong on the kilometre rate only.");
        if (includedKm != null && includedKm.signum() < 0) throw new IllegalArgumentException("The included kilometres cannot be negative.");
        this.includedKm = includedKm == null ? null : includedKm.setScale(2, RoundingMode.HALF_UP);
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
