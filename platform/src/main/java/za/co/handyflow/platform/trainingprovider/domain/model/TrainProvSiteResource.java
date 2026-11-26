package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ResourceRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A piece of equipment or similar resource held at a delivery site. Never deleted: retired with a reason. */
@Entity(name = "TrainProvSiteResource")
@Table(name = "trainprov_site_resources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSiteResource {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "resource_no", nullable = false, updatable = false)
    private String resourceNo;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "venue_id")
    private UUID venueId;
    @Column(nullable = false)
    private String name;
    @Column(name = "resource_type", nullable = false)
    private String resourceType;
    private String identifier;
    @Column(nullable = false)
    private int quantity = 1;
    @Column(nullable = false)
    private boolean shareable;
    private String notes;
    @Column(nullable = false)
    private String status = ResourceRules.IN_SERVICE;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(name = "status_changed_on")
    private LocalDate statusChangedOn;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvSiteResource create(TenantId t, String no, UUID siteId, UUID venueId, String name, String type, String identifier, int quantity, boolean shareable,
                                               String notes, UUID by) {
        String problem = ResourceRules.problem(name, type, quantity);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        TrainProvSiteResource r = new TrainProvSiteResource();
        r.tenantId = t.getValue();
        r.resourceNo = no;
        r.siteId = siteId;
        r.venueId = venueId;
        r.apply(name, type, identifier, quantity, shareable, notes);
        r.createdBy = by;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    private void apply(String name, String type, String identifier, int quantity, boolean shareable, String notes) {
        this.name = name.trim();
        this.resourceType = type;
        this.identifier = identifier == null || identifier.isBlank() ? null : identifier.trim();
        this.quantity = quantity;
        this.shareable = shareable;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void change(UUID venueId, String name, String type, String identifier, int quantity, boolean shareable, String notes) {
        if (ResourceRules.RETIRED.equals(status)) {
            throw new IllegalStateException("A retired resource cannot be changed");
        }
        String problem = ResourceRules.problem(name, type, quantity);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.venueId = venueId;
        apply(name, type, identifier, quantity, shareable, notes);
        this.updatedAt = Instant.now();
    }

    public void setStatus(String to, String reason, LocalDate on, LocalDate today) {
        String problem = ResourceRules.statusProblem(status, to, reason, on, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = to;
        this.statusReason = ResourceRules.IN_SERVICE.equals(to) ? null : reason.trim();
        this.statusChangedOn = on;
        this.updatedAt = Instant.now();
    }

    public void moveTo(UUID newSiteId, UUID newVenueId) {
        this.siteId = newSiteId;
        this.venueId = newVenueId;
        this.updatedAt = Instant.now();
    }
}
