package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sc_stock_locations")
@Getter
@NoArgsConstructor
public class ScStockLocation {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(nullable = false, length = 100) String name;
    @Column(name = "location_type", nullable = false, length = 20) String locationType = "WAREHOUSE";
    String address;
    @Column(name = "is_default", nullable = false) boolean isDefault = false;
    @Column(nullable = false) boolean active = true;
    @Column(name = "created_at") Instant createdAt;

    public static ScStockLocation create(UUID tenantId, String name,
                                         String locationType, String address, boolean isDefault) {
        ScStockLocation l = new ScStockLocation();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.name = name;
        l.locationType = locationType != null ? locationType : "WAREHOUSE";
        l.address = address;
        l.isDefault = isDefault;
        l.active = true;
        l.createdAt = Instant.now();
        return l;
    }
}
