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
 * The current insurance profile of a vehicle (one row per vehicle). Answers "is this
 * vehicle insured, and what exactly is the cover" — the existing
 * {@code Vehicle.insuranceExpiry} only answered "when". Renewals overwrite this row;
 * the superseded policy schedules are preserved as versioned INSURANCE documents.
 * Claim history arrives with the accident register (a later chunk).
 */
@Entity
@Table(name = "fleet_vehicle_insurance")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleInsurance {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false, unique = true)
    private UUID vehicleId;

    @Column(nullable = false)
    private boolean insured = false;

    private String insurer;

    @Column(name = "policy_number", length = 100)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", length = 30)
    private InsurancePolicyType policyType;

    @Column(name = "policy_start")
    private LocalDate policyStart;

    @Column(name = "policy_expiry")
    private LocalDate policyExpiry;

    @Column(precision = 15, scale = 2)
    private BigDecimal premium;

    @Column(precision = 15, scale = 2)
    private BigDecimal excess;

    private String broker;

    @Column(name = "broker_contact")
    private String brokerContact;

    @Column(name = "cover_amount", precision = 15, scale = 2)
    private BigDecimal coverAmount;

    @Column(name = "vehicle_value", precision = 15, scale = 2)
    private BigDecimal vehicleValue;

    @Column(name = "tracking_required", nullable = false)
    private boolean trackingRequired = false;

    @Column(name = "tracking_provider")
    private String trackingProvider;

    @Column(name = "tracking_device_number", length = 100)
    private String trackingDeviceNumber;

    @Column(name = "windscreen_cover", nullable = false)
    private boolean windscreenCover = false;

    @Column(name = "third_party_cover_amount", precision = 15, scale = 2)
    private BigDecimal thirdPartyCoverAmount;

    @Column(name = "special_conditions", columnDefinition = "TEXT")
    private String specialConditions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static VehicleInsurance create(TenantId tenantId, UUID vehicleId) {
        VehicleInsurance i = new VehicleInsurance();
        i.tenantId = tenantId;
        i.vehicleId = vehicleId;
        i.createdAt = Instant.now();
        i.updatedAt = Instant.now();
        return i;
    }

    /** Full-replace of the profile. Cross-field validation (insured ⇒ insurer + expiry) lives in the service. */
    public void apply(boolean insured, String insurer, String policyNumber, InsurancePolicyType policyType,
                      LocalDate policyStart, LocalDate policyExpiry, BigDecimal premium, BigDecimal excess,
                      String broker, String brokerContact, BigDecimal coverAmount, BigDecimal vehicleValue,
                      boolean trackingRequired, String trackingProvider, String trackingDeviceNumber,
                      boolean windscreenCover, BigDecimal thirdPartyCoverAmount, String specialConditions,
                      UUID updatedBy) {
        this.insured = insured;
        this.insurer = insurer;
        this.policyNumber = policyNumber;
        this.policyType = policyType;
        this.policyStart = policyStart;
        this.policyExpiry = policyExpiry;
        this.premium = premium;
        this.excess = excess;
        this.broker = broker;
        this.brokerContact = brokerContact;
        this.coverAmount = coverAmount;
        this.vehicleValue = vehicleValue;
        this.trackingRequired = trackingRequired;
        this.trackingProvider = trackingProvider;
        this.trackingDeviceNumber = trackingDeviceNumber;
        this.windscreenCover = windscreenCover;
        this.thirdPartyCoverAmount = thirdPartyCoverAmount;
        this.specialConditions = specialConditions;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public boolean hasTrackingDevice() {
        return trackingDeviceNumber != null && !trackingDeviceNumber.isBlank();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}