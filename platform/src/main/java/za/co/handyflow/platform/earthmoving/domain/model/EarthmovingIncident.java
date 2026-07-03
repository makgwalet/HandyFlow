package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An incident (breakdown, accident, theft, fire, etc.) reported against a
 * piece of equipment. Previously this only existed as ephemeral React state
 * on the frontend — reloading the page silently lost every incident ever
 * reported. That's not a viable state for anything touching safety or
 * insurance/liability records, hence this entity.
 * <p>
 * NAMED "EarthmovingIncident" rather than plain "Incident" because the
 * security module already has its own Incident concept (guard/dispatch
 * incidents). Spring's bean scanning and Hibernate's entity-name resolution
 * both default to the simple class name, so two classes named `Incident` in
 * different packages collide at startup even though Java itself is fine
 * with same-name classes in different packages — see EarthmovingIncidentRepository
 * and EarthmovingIncidentService for the same issue one layer down.
 */
@Entity
@Table(name = "earthmoving_incidents")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EarthmovingIncident {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private String type;     // BREAKDOWN | ACCIDENT | THEFT | FIRE | ROLLOVER | NEAR_MISS | FUEL_SPILL | OTHER

    @Column(nullable = false)
    private String severity; // LOW | MEDIUM | HIGH | CRITICAL

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "site_name")
    private String siteName;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private String status = "OPEN"; // OPEN | RESOLVED

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "reported_by_user_id")
    private UUID reportedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static EarthmovingIncident create(TenantId tenantId, UUID assetId, String type, String severity,
                                             String title, String description, String operatorName,
                                             String siteName, Double latitude, Double longitude,
                                             UUID reportedByUserId) {
        EarthmovingIncident i = new EarthmovingIncident();
        i.tenantId = tenantId;
        i.assetId = assetId;
        i.type = type;
        i.severity = severity;
        i.title = title;
        i.description = description;
        i.operatorName = operatorName;
        i.siteName = siteName;
        i.latitude = latitude;
        i.longitude = longitude;
        i.reportedByUserId = reportedByUserId;
        i.status = "OPEN";
        i.reportedAt = Instant.now();
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    public void resolve(UUID resolvedByUserId, String resolutionNotes) {
        if ("RESOLVED".equals(this.status)) {
            throw new IllegalStateException("Incident " + id + " is already resolved");
        }
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
        this.resolvedByUserId = resolvedByUserId;
        this.resolutionNotes = resolutionNotes;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}