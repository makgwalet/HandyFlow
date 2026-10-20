package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A theft / missing-vehicle case: when and where, the SAPS case, the insurer's claim,
 * tracking information, and — once it happens — recovery.
 * <p>
 * A MISSING report that is later upgraded to STOLEN is marked SUPERSEDED (the new STOLEN
 * report carries on), so there is only ever one open report per vehicle.
 */
@Entity
@Table(name = "fleet_vehicle_theft_reports")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleTheftReport {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TheftKind kind;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 500)
    private String location;

    @Column(name = "police_case_number", length = 100)
    private String policeCaseNumber;

    @Column(name = "saps_station")
    private String sapsStation;

    @Column(name = "police_reference", length = 100)
    private String policeReference;

    private String insurer;

    @Column(name = "claim_number", length = 100)
    private String claimNumber;

    @Column(name = "tracking_info", columnDefinition = "TEXT")
    private String trackingInfo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_status", nullable = false, length = 20)
    private RecoveryStatus recoveryStatus = RecoveryStatus.NOT_RECOVERED;

    @Column(name = "recovered_at")
    private Instant recoveredAt;

    @Column(name = "recovery_location", length = 500)
    private String recoveryLocation;

    @Column(name = "recovery_notes", columnDefinition = "TEXT")
    private String recoveryNotes;

    public static VehicleTheftReport create(TenantId tenantId, UUID vehicleId, UUID eventId, TheftKind kind,
                                            Instant occurredAt, String location, String policeCaseNumber,
                                            String sapsStation, String policeReference, String insurer,
                                            String claimNumber, String trackingInfo, String notes) {
        VehicleTheftReport r = new VehicleTheftReport();
        r.tenantId = tenantId;
        r.vehicleId = vehicleId;
        r.eventId = eventId;
        r.kind = kind;
        r.occurredAt = occurredAt;
        r.updateDetails(location, policeCaseNumber, sapsStation, policeReference, insurer, claimNumber,
                trackingInfo, notes);
        return r;
    }

    /** Police / insurer / tracking details arrive piecemeal after the report, so they stay editable while the vehicle is unrecovered. */
    public void updateDetails(String location, String policeCaseNumber, String sapsStation,
                              String policeReference, String insurer, String claimNumber,
                              String trackingInfo, String notes) {
        if (recoveryStatus != RecoveryStatus.NOT_RECOVERED) {
            throw new IllegalStateException("This report is " + recoveryStatus + " and can no longer be edited");
        }
        this.location = location;
        this.policeCaseNumber = policeCaseNumber;
        this.sapsStation = sapsStation;
        this.policeReference = policeReference;
        this.insurer = insurer;
        this.claimNumber = claimNumber;
        this.trackingInfo = trackingInfo;
        this.notes = notes;
    }

    public void markRecovered(RecoveryStatus status, Instant recoveredAt, String location, String notes) {
        if (recoveryStatus != RecoveryStatus.NOT_RECOVERED) {
            throw new IllegalStateException("This report is already " + recoveryStatus);
        }
        if (status != RecoveryStatus.RECOVERED && status != RecoveryStatus.RECOVERED_DAMAGED) {
            throw new IllegalArgumentException("Recovery status must be RECOVERED or RECOVERED_DAMAGED");
        }
        if (recoveredAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("Recovery cannot be before the vehicle went missing");
        }
        this.recoveryStatus = status;
        this.recoveredAt = recoveredAt;
        this.recoveryLocation = location;
        this.recoveryNotes = notes;
    }

    public void supersede() {
        this.recoveryStatus = RecoveryStatus.SUPERSEDED;
    }

    public boolean isOpen() { return recoveryStatus == RecoveryStatus.NOT_RECOVERED; }
}