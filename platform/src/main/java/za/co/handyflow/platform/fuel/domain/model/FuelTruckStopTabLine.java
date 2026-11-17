package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** One line on a tab: a truck stop service, its Bookings price at the moment it was added, and a quantity. The name and price are kept as they were. */
@Entity
@Table(name = "fuel_truck_stop_tab_lines")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopTabLine {

    static final BigDecimal MAX_QTY = new BigDecimal("999");

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tab_id", nullable = false, updatable = false) private UUID tabId;
    @Column(name = "offering_id", nullable = false, updatable = false) private UUID offeringId;
    @Column(nullable = false, updatable = false, length = 200) private String description;
    @Column(nullable = false, updatable = false, precision = 10, scale = 2) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "added_by", updatable = false) private UUID addedBy;
    @Column(name = "added_at", nullable = false, updatable = false) private Instant addedAt = Instant.now();

    public static FuelTruckStopTabLine of(TenantId tenantId, UUID tabId, UUID offeringId, String description, BigDecimal quantity, BigDecimal unitPrice, UUID by) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Enter a quantity above 0.");
        if (quantity.compareTo(MAX_QTY) > 0) throw new IllegalArgumentException("The quantity is too large.");
        if (unitPrice == null || unitPrice.signum() <= 0) throw new IllegalArgumentException("This service has no price in Bookings. Set its price there first.");
        FuelTruckStopTabLine l = new FuelTruckStopTabLine();
        l.tenantId = tenantId; l.tabId = tabId; l.offeringId = offeringId; l.addedBy = by;
        l.description = description.length() > 200 ? description.substring(0, 200) : description;
        l.quantity = quantity.setScale(2, RoundingMode.HALF_UP); l.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        return l;
    }

    public BigDecimal amount() { return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP); }
}
