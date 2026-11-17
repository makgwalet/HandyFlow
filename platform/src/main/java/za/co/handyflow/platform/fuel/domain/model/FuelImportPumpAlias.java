package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** What one import source calls a pump, mapped to the Fuel pump. Source and alias are kept lower-case and trimmed so matching ignores case. */
@Entity
@Table(name = "fuel_import_pump_aliases")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelImportPumpAlias {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 40)
    private String source;

    @Column(nullable = false, updatable = false, length = 40)
    private String alias;

    @Column(name = "pump_id", nullable = false, updatable = false)
    private UUID pumpId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static String normalise(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    public static FuelImportPumpAlias of(TenantId tenantId, String source, String alias, UUID pumpId, UUID by) {
        String s = normalise(source), a = normalise(alias);
        if (s.isEmpty() || s.length() > 40) throw new IllegalArgumentException("Enter the source (up to 40 characters).");
        if (a.isEmpty() || a.length() > 40) throw new IllegalArgumentException("Enter the pump label the sender uses (up to 40 characters).");
        FuelImportPumpAlias x = new FuelImportPumpAlias();
        x.tenantId = tenantId; x.source = s; x.alias = a; x.pumpId = pumpId; x.createdBy = by;
        return x;
    }
}
