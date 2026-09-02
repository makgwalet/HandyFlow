package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single fertiliser/pesticide/herbicide/fungicide/irrigation application
 * against one {@link AgCropCycle}. Irrigation is folded in as one more
 * {@code inputType} rather than given its own entity — an irrigation event
 * shares every structural property an input application already has (a
 * date, a quantity, a unit, who did it, an optional cost), so a separate
 * {@code AgIrrigationRecord} table would duplicate this entity for no
 * functional gain, the same "avoid unnecessary complexity" reasoning
 * behind {@link AgProductionArea}'s own consolidation in Increment 1.
 * <p>
 * {@code inventoryItemId} is nullable — a farm can log an application
 * without tracking it against a formal {@link AgInventoryItem}, and when
 * it IS set the application service issues a matching
 * {@link AgStockMovement} (ISSUE), mirroring {@link AgFeedRecord}'s own
 * pattern from Increment 1. Append-only history.
 */
@Entity
@Table(name = "ag_input_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgInputApplication {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate;

    /** FERTILISER | PESTICIDE | HERBICIDE | FUNGICIDE | IRRIGATION | OTHER */
    @Column(name = "input_type", nullable = false)
    private String inputType;

    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "product_used")
    private String productUsed;

    @Column(name = "quantity_applied", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityApplied;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    @Column(name = "application_method")
    private String applicationMethod;

    @Column(name = "applied_by")
    private UUID appliedBy;

    @Column(name = "applied_by_name")
    private String appliedByName;

    @Column(name = "labor_hours", precision = 8, scale = 2)
    private BigDecimal laborHours;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "weather_conditions")
    private String weatherConditions;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgInputApplication create(TenantId tenantId, UUID cropCycleId, LocalDate applicationDate,
                                             String inputType, UUID inventoryItemId, String productUsed,
                                             BigDecimal quantityApplied, String unitOfMeasure, String applicationMethod,
                                             UUID appliedBy, String appliedByName, BigDecimal laborHours,
                                             BigDecimal cost, String weatherConditions, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (cropCycleId == null) throw new IllegalArgumentException("cropCycleId is required");
        if (applicationDate == null) throw new IllegalArgumentException("applicationDate is required");
        if (inputType == null || inputType.isBlank()) throw new IllegalArgumentException("inputType is required");
        if (quantityApplied == null || quantityApplied.signum() <= 0) throw new IllegalArgumentException("quantityApplied must be positive");
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) throw new IllegalArgumentException("unitOfMeasure is required");

        AgInputApplication a = new AgInputApplication();
        a.tenantId = tenantId;
        a.cropCycleId = cropCycleId;
        a.applicationDate = applicationDate;
        a.inputType = inputType;
        a.inventoryItemId = inventoryItemId;
        a.productUsed = productUsed;
        a.quantityApplied = quantityApplied;
        a.unitOfMeasure = unitOfMeasure;
        a.applicationMethod = applicationMethod;
        a.appliedBy = appliedBy;
        a.appliedByName = appliedByName;
        a.laborHours = laborHours;
        a.cost = cost;
        a.weatherConditions = weatherConditions;
        a.notes = notes;
        a.createdAt = Instant.now();
        return a;
    }
}
