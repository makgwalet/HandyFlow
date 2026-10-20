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
 * An accident as a proper case rather than a note: what happened, the other party, the
 * police report, the insurance claim, the repair, and (as {@link AccidentEvidence}) the
 * supporting files.
 * <p>
 * Three independent tracks live on one record:
 * <ul>
 *   <li>the <b>case</b> ({@link AccidentStatus}) — reported → assessed → repaired → closed;</li>
 *   <li>the <b>claim</b> ({@link ClaimStatus}) — only if an insurer is involved;</li>
 *   <li>the <b>repair</b> — repairer, cost and return-to-service date, whether or not insured.</li>
 * </ul>
 * A case cannot be CLOSED while a lodged claim is still unresolved.
 */
@Entity
@Table(name = "fleet_accidents")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Accident {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "reference_number", nullable = false, length = 30)
    private String referenceNumber;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 500)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WeatherCondition weather;

    @Enumerated(EnumType.STRING)
    @Column(name = "road_condition", length = 20)
    private RoadCondition roadCondition;

    // ── Other party ─────────────────────────────────────────────────────
    @Column(name = "other_vehicle_registration", length = 30)
    private String otherVehicleRegistration;

    @Column(name = "other_driver_name")
    private String otherDriverName;

    @Column(name = "other_driver_contact")
    private String otherDriverContact;

    @Column(name = "other_insurer")
    private String otherInsurer;

    // ── Police ──────────────────────────────────────────────────────────
    @Column(name = "police_case_number", length = 100)
    private String policeCaseNumber;

    @Column(name = "saps_station")
    private String sapsStation;

    @Column(name = "police_reference", length = 100)
    private String policeReference;

    // ── What happened ───────────────────────────────────────────────────
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String witnesses;

    @Column(name = "injuries_count", nullable = false)
    private int injuriesCount = 0;

    @Column(name = "fatalities_count", nullable = false)
    private int fatalitiesCount = 0;

    @Column(name = "estimated_damage", precision = 15, scale = 2)
    private BigDecimal estimatedDamage;

    @Column(name = "third_party_damage", precision = 15, scale = 2)
    private BigDecimal thirdPartyDamage;

    @Column(name = "vehicle_drivable", nullable = false)
    private boolean vehicleDrivable = true;

    // ── Case ────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccidentStatus status = AccidentStatus.REPORTED;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "closed_at")
    private Instant closedAt;

    // ── Claim ───────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 20)
    private ClaimStatus claimStatus = ClaimStatus.NOT_CLAIMED;

    @Column(name = "claim_insurer")
    private String claimInsurer;

    @Column(name = "claim_number", length = 100)
    private String claimNumber;

    @Column(name = "claim_excess", precision = 15, scale = 2)
    private BigDecimal claimExcess;

    @Column(name = "assessor_name")
    private String assessorName;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "claim_submitted_at")
    private LocalDate claimSubmittedAt;

    @Column(name = "claim_settlement_amount", precision = 15, scale = 2)
    private BigDecimal claimSettlementAmount;

    @Column(name = "claim_settled_at")
    private LocalDate claimSettledAt;

    // ── Repair ──────────────────────────────────────────────────────────
    private String repairer;

    @Column(name = "repair_cost", precision = 15, scale = 2)
    private BigDecimal repairCost;

    @Column(name = "returned_to_service_at")
    private LocalDate returnedToServiceAt;

    @Column(name = "reported_by")
    private UUID reportedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // ── Factory & updates ───────────────────────────────────────────────

    public static Accident report(TenantId tenantId, String referenceNumber, UUID vehicleId,
                                  AccidentDetails details, UUID reportedBy) {
        Accident a = new Accident();
        a.tenantId = tenantId;
        a.referenceNumber = referenceNumber;
        a.vehicleId = vehicleId;
        a.reportedBy = reportedBy;
        a.createdAt = Instant.now();
        a.copyDetails(details);
        return a;
    }

    /** Edits the description of the accident. Not allowed once the case is CLOSED or WRITTEN_OFF. */
    public void applyDetails(AccidentDetails details) {
        if (status == AccidentStatus.CLOSED || status == AccidentStatus.WRITTEN_OFF) {
            throw new IllegalStateException("Accident " + referenceNumber + " is " + status
                    + " and its details can no longer be edited" + (status == AccidentStatus.CLOSED ? " — reopen it first" : ""));
        }
        copyDetails(details);
    }

    /** Pre-fills the claim insurer / excess from the vehicle's insurance profile when the accident is first reported. */
    public void prefillClaimInsurer(String insurer, BigDecimal excess) {
        this.claimInsurer = insurer;
        this.claimExcess = excess;
    }

    /**
     * Updates the insurance claim. Rules: legal {@link ClaimStatus} transition; an insurer
     * once a claim is lodged; a settlement amount to mark it SETTLED; submitted/settled
     * dates default to today. Setting NOT_CLAIMED (never claimed, or withdrawn) clears the
     * claim number, dates and settlement.
     */
    public void updateClaim(ClaimStatus newStatus, String insurer, String claimNumber, BigDecimal excess,
                            String assessorName, LocalDate assessmentDate, LocalDate submittedAt,
                            BigDecimal settlementAmount, LocalDate settledAt) {
        requireNotClosed();
        if (!claimStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot change claim status from " + claimStatus + " to " + newStatus);
        }
        this.claimInsurer = insurer;
        this.claimExcess = excess;
        this.assessorName = assessorName;
        this.assessmentDate = assessmentDate;

        if (newStatus == ClaimStatus.NOT_CLAIMED) {
            this.claimNumber = null;
            this.claimSubmittedAt = null;
            this.claimSettlementAmount = null;
            this.claimSettledAt = null;
        } else {
            if (insurer == null) {
                throw new IllegalArgumentException("An insurer is required once a claim has been lodged");
            }
            if (newStatus == ClaimStatus.SETTLED && settlementAmount == null) {
                throw new IllegalArgumentException("A settlement amount is required to mark the claim as settled");
            }
            LocalDate submitted = submittedAt != null ? submittedAt
                    : (claimSubmittedAt != null ? claimSubmittedAt : LocalDate.now());
            LocalDate settled = newStatus == ClaimStatus.SETTLED
                    ? (settledAt != null ? settledAt : LocalDate.now()) : null;
            if (settled != null && settled.isBefore(submitted)) {
                throw new IllegalArgumentException("Settlement date cannot be before the claim submission date");
            }
            this.claimNumber = claimNumber;
            this.claimSubmittedAt = submitted;
            this.claimSettlementAmount = settlementAmount;
            this.claimSettledAt = settled;
        }
        this.claimStatus = newStatus;
        this.updatedAt = Instant.now();
    }

    public void updateRepair(String repairer, BigDecimal repairCost, LocalDate returnedToServiceAt) {
        requireNotClosed();
        if (returnedToServiceAt != null
                && returnedToServiceAt.isBefore(occurredAt.atZone(java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate())) {
            throw new IllegalArgumentException("Return-to-service date cannot be before the accident date");
        }
        this.repairer = repairer;
        this.repairCost = repairCost;
        this.returnedToServiceAt = returnedToServiceAt;
        this.updatedAt = Instant.now();
    }

    /** Moves the case along. CLOSED requires the claim (if any) to be resolved; closing/write-off stamps the time, reopening clears it. */
    public void changeStatus(AccidentStatus target, String note) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot change accident status from " + status + " to " + target);
        }
        if (target == AccidentStatus.CLOSED && claimStatus.isUnresolved()) {
            throw new IllegalStateException("The insurance claim is still " + claimStatus
                    + " — settle, reject or withdraw it before closing this case");
        }
        this.status = target;
        if (target == AccidentStatus.CLOSED || target == AccidentStatus.WRITTEN_OFF) {
            this.closedAt = Instant.now();
            this.resolutionNote = note;
        } else {
            this.closedAt = null;
            this.resolutionNote = null;
        }
        this.updatedAt = Instant.now();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void requireNotClosed() {
        if (status == AccidentStatus.CLOSED) {
            throw new IllegalStateException("Accident " + referenceNumber + " is CLOSED — reopen it first");
        }
    }

    private void copyDetails(AccidentDetails d) {
        this.driverId = d.driverId();
        this.driverName = d.driverName();
        this.tripId = d.tripId();
        this.occurredAt = d.occurredAt();
        this.location = d.location();
        this.weather = d.weather();
        this.roadCondition = d.roadCondition();
        this.otherVehicleRegistration = d.otherVehicleRegistration();
        this.otherDriverName = d.otherDriverName();
        this.otherDriverContact = d.otherDriverContact();
        this.otherInsurer = d.otherInsurer();
        this.policeCaseNumber = d.policeCaseNumber();
        this.sapsStation = d.sapsStation();
        this.policeReference = d.policeReference();
        this.description = d.description();
        this.witnesses = d.witnesses();
        this.injuriesCount = d.injuriesCount();
        this.fatalitiesCount = d.fatalitiesCount();
        this.estimatedDamage = d.estimatedDamage();
        this.thirdPartyDamage = d.thirdPartyDamage();
        this.vehicleDrivable = d.vehicleDrivable();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}