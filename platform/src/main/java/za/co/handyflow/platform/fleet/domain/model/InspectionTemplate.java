package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable checklist (daily walk-around, pre-trip, driver handover, ...).
 * <p>
 * Editing a template replaces its items, which is safe for history: every inspection
 * copies ("snapshots") the questions it was started with, so past inspections never
 * change when a template does.
 * <p>
 * {@code intervalDays} turns a template into a schedule: the compliance board flags a
 * vehicle as due when it has no completed inspection from this template within that
 * many days (optionally limited to one vehicle type via {@code appliesToVehicleType}).
 */
@Entity
@Table(name = "fleet_inspection_templates")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InspectionTemplate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false, length = 30)
    private InspectionType inspectionType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "applies_to_vehicle_type", length = 50)
    private String appliesToVehicleType;

    @Column(name = "signature_required", nullable = false)
    private boolean signatureRequired = false;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<InspectionTemplateItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    public static InspectionTemplate create(TenantId tenantId, String name, InspectionType type,
                                            String description, Integer intervalDays,
                                            String appliesToVehicleType, boolean signatureRequired,
                                            List<TemplateItemSpec> specs) {
        InspectionTemplate t = new InspectionTemplate();
        t.tenantId = tenantId;
        t.createdAt = Instant.now();
        t.update(name, type, description, intervalDays, appliesToVehicleType, signatureRequired, true, specs);
        return t;
    }

    public void update(String name, InspectionType type, String description, Integer intervalDays,
                       String appliesToVehicleType, boolean signatureRequired, boolean active,
                       List<TemplateItemSpec> specs) {
        this.name = name;
        this.inspectionType = type;
        this.description = description;
        this.intervalDays = intervalDays;
        this.appliesToVehicleType = appliesToVehicleType;
        this.signatureRequired = signatureRequired;
        this.active = active;
        this.items.clear();
        for (int i = 0; i < specs.size(); i++) {
            this.items.add(InspectionTemplateItem.of(this, specs.get(i), i));
        }
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.active = false;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}