package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.agriculture.domain.rules.AgPartyRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A movement — between production areas on the same farm, between two of
 * the tenant's own farms, or on/off the farm entirely (purchase-in,
 * sale-out, transfer, show-and-return) — against either one
 * {@link AgAnimal} or a partial/whole {@link AgGroup} (via
 * {@code countMoved}). Append-only history, matching
 * {@code earthmoving.OperatorLog}'s convention.
 * <p>
 * Deliberately does NOT create a commercial sale/invoice record itself —
 * {@code SALE_OUT} only records that the movement happened and why; the
 * actual invoice, if any, belongs to the platform's {@code invoicing}/
 * {@code crm} modules per this module's own package-info.java.
 */
@Entity
@Table(name = "ag_movement_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgMovementRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    /** INTERNAL_TRANSFER | PURCHASE_IN | SALE_OUT | TRANSFER_OUT | TRANSFER_IN | SHOW_OUT | SHOW_RETURN */
    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "from_production_area_id")
    private UUID fromProductionAreaId;

    @Column(name = "to_production_area_id")
    private UUID toProductionAreaId;

    /** Set only for a movement between two of the tenant's own farms. */
    @Column(name = "from_farm_id")
    private UUID fromFarmId;

    @Column(name = "to_farm_id")
    private UUID toFarmId;

    /** Set only for a partial group movement — null means the whole animal/group moved. */
    @Column(name = "count_moved")
    private Integer countMoved;

    private String reason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Who a sale or transfer out went to, where, and in what vehicle: the details a document of identification needs. */
    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_id_number")
    private String buyerIdNumber;

    @Column(name = "buyer_address")
    private String buyerAddress;

    private String destination;

    @Column(name = "vehicle_registration")
    private String vehicleRegistration;

    /** Why the sale or transfer went ahead during a withdrawal period; null when nothing was held. */
    @Column(name = "withdrawal_override_reason", columnDefinition = "TEXT")
    private String withdrawalOverrideReason;

    /** The day that withdrawal period would have run to. */
    @Column(name = "withdrawal_held_until")
    private LocalDate withdrawalHeldUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgMovementRecord create(TenantId tenantId, UUID animalId, UUID groupId, LocalDate movementDate,
                                           String movementType, UUID fromProductionAreaId, UUID toProductionAreaId,
                                           UUID fromFarmId, UUID toFarmId, Integer countMoved, String reason, String notes) {
        return create(tenantId, animalId, groupId, movementDate, movementType, fromProductionAreaId, toProductionAreaId,
                fromFarmId, toFarmId, countMoved, reason, notes, null, null, null, null, null);
    }

    public static AgMovementRecord create(TenantId tenantId, UUID animalId, UUID groupId, LocalDate movementDate,
                                           String movementType, UUID fromProductionAreaId, UUID toProductionAreaId,
                                           UUID fromFarmId, UUID toFarmId, Integer countMoved, String reason, String notes,
                                           String buyerName, String buyerIdNumber, String buyerAddress, String destination,
                                           String vehicleRegistration) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgTrackingTarget.requireExactlyOne(animalId, groupId);
        if (movementDate == null) throw new IllegalArgumentException("movementDate is required");
        if (movementType == null || movementType.isBlank()) throw new IllegalArgumentException("movementType is required");

        AgMovementRecord m = new AgMovementRecord();
        m.tenantId = tenantId;
        m.animalId = animalId;
        m.groupId = groupId;
        m.movementDate = movementDate;
        m.movementType = movementType;
        m.fromProductionAreaId = fromProductionAreaId;
        m.toProductionAreaId = toProductionAreaId;
        m.fromFarmId = fromFarmId;
        m.toFarmId = toFarmId;
        m.countMoved = countMoved;
        m.reason = reason;
        m.notes = notes;
        m.buyerName = AgPartyRules.clean(buyerName, "Buyer name", AgPartyRules.NAME);
        m.buyerIdNumber = AgPartyRules.clean(buyerIdNumber, "Buyer ID number", AgPartyRules.ID_NUMBER);
        m.buyerAddress = AgPartyRules.clean(buyerAddress, "Buyer address", AgPartyRules.ADDRESS);
        m.destination = AgPartyRules.clean(destination, "Destination", AgPartyRules.ADDRESS);
        m.vehicleRegistration = AgPartyRules.vehicle(vehicleRegistration);
        m.createdAt = Instant.now();
        return m;
    }

    /** Keeps the reason for going ahead during a withdrawal period, on the record the sale made. */
    public void recordWithdrawalOverride(AgWithdrawalRules.Override override) {
        if (override == null) return;
        this.withdrawalOverrideReason = override.reason();
        this.withdrawalHeldUntil = override.heldUntil();
    }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }
}
