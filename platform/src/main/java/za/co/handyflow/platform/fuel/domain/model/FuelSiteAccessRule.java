package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a delivery site lets in. Every limit is optional and a site with no rule restricts nothing. The site's own
 * limits are the customer's, so nothing here is a regulatory interpretation.
 */
@Entity
@Table(name = "fuel_site_access_rules")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSiteAccessRule {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @Column(name = "max_capacity_litres", precision = 10, scale = 2)
    private BigDecimal maxCapacityLitres;

    @Column(name = "max_gvm_kg")
    private Integer maxGvmKg;

    @Column(name = "max_length_m", precision = 5, scale = 2)
    private BigDecimal maxLengthM;

    @Column(name = "max_width_m", precision = 5, scale = 2)
    private BigDecimal maxWidthM;

    @Column(name = "max_height_m", precision = 5, scale = 2)
    private BigDecimal maxHeightM;

    /** Comma separated upper case tanker types the site accepts; empty means any. */
    @Column(name = "approved_types", length = 300)
    private String approvedTypes;

    @Column(name = "access_notes", length = 500)
    private String accessNotes;

    /** When true only drivers approved for this site (see FuelSiteDriverApproval) may serve it. */
    @Column(name = "require_approved_drivers", nullable = false)
    private boolean requireApprovedDrivers = false;

    /** Name of a safety training the site requires of its drivers; empty means none. The approval records the driver's evidence. */
    @Column(name = "required_training", length = 120)
    private String requiredTraining;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static FuelSiteAccessRule forSite(TenantId tenantId, UUID siteId) {
        FuelSiteAccessRule r = new FuelSiteAccessRule();
        r.tenantId = tenantId; r.siteId = siteId;
        return r;
    }

    public void update(BigDecimal maxCapacityLitres, Integer maxGvmKg, BigDecimal maxLengthM, BigDecimal maxWidthM, BigDecimal maxHeightM,
                       Set<String> approvedTypes, String accessNotes, boolean requireApprovedDrivers, String requiredTraining, UUID by) {
        requirePositive(maxCapacityLitres, "The maximum capacity");
        requirePositive(maxLengthM, "The maximum length"); requirePositive(maxWidthM, "The maximum width"); requirePositive(maxHeightM, "The maximum height");
        if (maxGvmKg != null && maxGvmKg <= 0) throw new IllegalArgumentException("The maximum gross vehicle mass must be more than zero.");
        this.maxCapacityLitres = maxCapacityLitres; this.maxGvmKg = maxGvmKg;
        this.maxLengthM = maxLengthM; this.maxWidthM = maxWidthM; this.maxHeightM = maxHeightM;
        Set<String> types = approvedTypes == null ? Set.of() : approvedTypes.stream().filter(t -> t != null && !t.isBlank()).map(t -> t.trim().toUpperCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String joined = String.join(",", types);
        if (joined.length() > 300) throw new IllegalArgumentException("The list of tanker types is too long.");
        this.approvedTypes = joined.isEmpty() ? null : joined;
        this.accessNotes = accessNotes == null || accessNotes.isBlank() ? null : (accessNotes.trim().length() > 500 ? accessNotes.trim().substring(0, 500) : accessNotes.trim());
        this.requireApprovedDrivers = requireApprovedDrivers;
        this.requiredTraining = requiredTraining == null || requiredTraining.isBlank() ? null : (requiredTraining.trim().length() > 120 ? requiredTraining.trim().substring(0, 120) : requiredTraining.trim());
        this.updatedBy = by; this.updatedAt = Instant.now();
    }

    public Set<String> approvedTypeSet() {
        return approvedTypes == null || approvedTypes.isBlank() ? Set.of() : Arrays.stream(approvedTypes.split(",")).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void requirePositive(BigDecimal v, String what) {
        if (v != null && v.signum() <= 0) throw new IllegalArgumentException(what + " must be more than zero.");
    }
}
