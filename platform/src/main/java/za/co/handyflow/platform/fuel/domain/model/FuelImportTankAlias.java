package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** What one gauge source calls a tank, mapped to the Fuel tank. Source and alias are kept lower-case and trimmed. */
@Entity
@Table(name = "fuel_import_tank_aliases")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelImportTankAlias {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 40)
    private String source;

    @Column(nullable = false, updatable = false, length = 60)
    private String alias;

    @Column(name = "tank_id", nullable = false, updatable = false)
    private UUID tankId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static String normalise(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    public static FuelImportTankAlias of(TenantId tenantId, String source, String alias, UUID tankId, UUID by) {
        String s = normalise(source), a = normalise(alias);
        if (s.isEmpty() || s.length() > 40) throw new IllegalArgumentException("Enter the source (up to 40 characters).");
        if (a.isEmpty() || a.length() > 60) throw new IllegalArgumentException("Enter the tank label the sender uses (up to 60 characters).");
        FuelImportTankAlias x = new FuelImportTankAlias();
        x.tenantId = tenantId; x.source = s; x.alias = a; x.tankId = tankId; x.createdBy = by;
        return x;
    }
}
