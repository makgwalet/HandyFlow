package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A contract's reference to an existing price entry. A link changes nothing about the price. */
@Entity
@Table(name = "fuel_contract_prices")
@IdClass(FuelContractPrice.Key.class)
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelContractPrice {

    @Id @Column(name = "contract_id") private UUID contractId;
    @Id @Column(name = "price_id") private UUID priceId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    /** Contract litres delivered before an order from which this price applies. 0 means no threshold. */
    @Column(name = "from_litres", nullable = false, precision = 14, scale = 2) private java.math.BigDecimal fromLitres = java.math.BigDecimal.ZERO;
    @Column(name = "linked_by") private UUID linkedBy;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt = Instant.now();

    public static FuelContractPrice of(TenantId tenantId, UUID contractId, UUID priceId, UUID by) {
        FuelContractPrice l = new FuelContractPrice();
        l.tenantId = tenantId; l.contractId = contractId; l.priceId = priceId; l.linkedBy = by;
        return l;
    }

    public void setFromLitres(java.math.BigDecimal v) {
        if (v == null) { fromLitres = java.math.BigDecimal.ZERO; return; }
        if (v.signum() < 0) throw new IllegalArgumentException("The volume cannot be negative.");
        fromLitres = v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public static class Key implements Serializable {
        private UUID contractId; private UUID priceId;
        public Key() {}
        public Key(UUID contractId, UUID priceId) { this.contractId = contractId; this.priceId = priceId; }
        @Override public boolean equals(Object o) { return o instanceof Key k && Objects.equals(contractId, k.contractId) && Objects.equals(priceId, k.priceId); }
        @Override public int hashCode() { return Objects.hash(contractId, priceId); }
    }
}
