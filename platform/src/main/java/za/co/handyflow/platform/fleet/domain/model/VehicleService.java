package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fleet_services")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleService {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String description;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "odometer_at_service")
    private Integer odometerAtService;

    // NEW: the next km at which service is due after this one
    @Column(name = "next_service_km")
    private Integer nextServiceKm;

    @Column(precision = 15, scale = 2)
    private BigDecimal cost;

    private String supplier;

    // NEW: invoice/job card reference for cost tracking
    @Column(name = "invoice_ref")
    private String invoiceRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    // ── Factory — matches FleetService.recordService call ─────────────────────

    public static VehicleService create(TenantId tenantId, UUID vehicleId,
                                        String type, String description,
                                        LocalDate serviceDate,
                                        Integer odometerAtService,
                                        Integer nextServiceKm,
                                        BigDecimal cost,
                                        String supplier,
                                        String invoiceRef) {
        VehicleService s = new VehicleService();
        s.tenantId          = tenantId;
        s.vehicleId         = vehicleId;
        s.type              = type;
        s.description       = description;
        s.serviceDate       = serviceDate;
        s.odometerAtService = odometerAtService;
        s.nextServiceKm     = nextServiceKm;
        s.cost              = cost;
        s.supplier          = supplier;
        s.invoiceRef        = invoiceRef;
        s.createdAt         = Instant.now();
        s.updatedAt         = Instant.now();
        return s;
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
