package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An implement or attachment — plough, slasher, forks, bucket — that is hitched to a tractor, plant or
 * forklift. One record for its life; each stay on a machine is an {@link AttachmentAssignment}.
 * Hours in use are the host machine's engine hours between attaching and detaching, so they are only
 * as good as that machine's hour meter.
 */
@Entity
@Table(name = "fleet_asset_attachments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AssetAttachment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttachmentStatus status = AttachmentStatus.AVAILABLE;

    @Column(name = "current_vehicle_id")
    private UUID currentVehicleId;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Column(name = "attached_engine_hours", precision = 10, scale = 1)
    private BigDecimal attachedEngineHours;

    @Column(name = "accumulated_hours", nullable = false, precision = 10, scale = 1)
    private BigDecimal accumulatedHours = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AssetAttachment create(TenantId tenantId, String name, String category, String serialNumber,
                                         String description, LocalDate purchaseDate, BigDecimal purchasePrice,
                                         String notes) {
        AssetAttachment a = new AssetAttachment();
        a.tenantId = tenantId;
        a.createdAt = Instant.now();
        a.updateDetails(name, category, serialNumber, description, purchaseDate, purchasePrice, notes);
        return a;
    }

    public void updateDetails(String name, String category, String serialNumber, String description,
                              LocalDate purchaseDate, BigDecimal purchasePrice, String notes) {
        this.name = name;
        this.category = category;
        this.serialNumber = serialNumber;
        this.description = description;
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.notes = notes;
        touch();
    }

    public void attach(UUID vehicleId, BigDecimal engineHours, Instant at) {
        if (status != AttachmentStatus.AVAILABLE) {
            throw new IllegalStateException("Attachment " + name + " is " + status + ", not available");
        }
        this.status = AttachmentStatus.ATTACHED;
        this.currentVehicleId = vehicleId;
        this.attachedEngineHours = engineHours;
        this.attachedAt = at;
        touch();
    }

    /** Banks the hours worked on this machine and frees the attachment. */
    public void detach(BigDecimal engineHours) {
        if (status != AttachmentStatus.ATTACHED) {
            throw new IllegalStateException("Attachment " + name + " is " + status + ", not attached");
        }
        if (engineHours.compareTo(attachedEngineHours) < 0) {
            throw new IllegalArgumentException("Detach hours (" + engineHours
                    + ") cannot be lower than the hours when it was attached (" + attachedEngineHours + ")");
        }
        this.accumulatedHours = accumulatedHours.add(engineHours.subtract(attachedEngineHours));
        this.status = AttachmentStatus.AVAILABLE;
        this.currentVehicleId = null;
        this.attachedEngineHours = null;
        this.attachedAt = null;
        touch();
    }

    public void retire() {
        if (status == AttachmentStatus.ATTACHED) {
            throw new IllegalStateException("Detach the attachment from its machine before retiring it");
        }
        this.status = AttachmentStatus.RETIRED;
        touch();
    }

    /** Lifetime hours in use, including the current stay given the host's present engine hours. */
    public BigDecimal totalHoursInUse(BigDecimal hostEngineHours) {
        if (status == AttachmentStatus.ATTACHED && attachedEngineHours != null && hostEngineHours != null) {
            return accumulatedHours.add(hostEngineHours.subtract(attachedEngineHours).max(BigDecimal.ZERO));
        }
        return accumulatedHours;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}