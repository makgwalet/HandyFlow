package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A driver, as a real linked entity — previously {@code Vehicle
 * .assignedDriverName} and {@code Trip.driverName} were pure free-text
 * strings with nothing behind them. That meant no way to track a driver's
 * own compliance documents, and critically, no way to notify a driver about
 * anything, since there was no contact detail attached to a name typed into
 * a text box.
 * <p>
 * SOUTH AFRICAN CONTEXT: a standard driving license (codes A/A1/B/C1/C/EB/
 * EC1/EC, corresponding to vehicle categories) is necessary but not
 * sufficient for professional driving — a PrDP (Professional Driving
 * Permit) is separately required for drivers of public transport vehicles
 * (category P), goods vehicles over a certain GVM (category G), or vehicles
 * carrying dangerous goods (category D). Both documents expire
 * independently and both are tracked here, mirroring the same
 * expiry-tracking pattern already established on {@link Vehicle} for
 * licence disc/roadworthy/insurance.
 * <p>
 * A driver is NOT necessarily a platform {@code User} — most drivers won't
 * have (or need) a login. That's why compliance notifications for a driver
 * use {@code Recipient.external(...)} (see FleetDriverService) rather than
 * requiring a linked user account — the same design that already lets the
 * notification module reach non-platform-user recipients generally.
 */
@Entity
@Table(name = "fleet_drivers")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Driver {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;
    private String email;

    @Column(name = "id_number")
    private String idNumber; // SA ID or passport number, optional

    // ── Standard driving license ────────────────────────────────────────
    @Column(name = "license_number")
    private String licenseNumber;

    @Column(name = "license_code")
    private String licenseCode; // A, A1, B, C1, C, EB, EC1, EC

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    // ── Professional Driving Permit (PrDP) ──────────────────────────────
    @Column(name = "prdp_required", nullable = false)
    private boolean prdpRequired = false;

    @Column(name = "prdp_number")
    private String prdpNumber;

    @Column(name = "prdp_category")
    private String prdpCategory; // G (goods), P (passengers), D (dangerous goods)

    @Column(name = "prdp_expiry")
    private LocalDate prdpExpiry;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    private String notes;

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

    public static Driver create(TenantId tenantId, String firstName, String lastName,
                                String phone, String email, String idNumber,
                                String licenseNumber, String licenseCode, LocalDate licenseExpiry,
                                boolean prdpRequired, String prdpNumber, String prdpCategory,
                                LocalDate prdpExpiry, String notes) {
        Driver d = new Driver();
        d.tenantId = tenantId;
        d.firstName = firstName;
        d.lastName = lastName;
        d.phone = phone;
        d.email = email;
        d.idNumber = idNumber;
        d.licenseNumber = licenseNumber;
        d.licenseCode = licenseCode;
        d.licenseExpiry = licenseExpiry;
        d.prdpRequired = prdpRequired;
        d.prdpNumber = prdpNumber;
        d.prdpCategory = prdpCategory;
        d.prdpExpiry = prdpExpiry;
        d.notes = notes;
        d.status = "ACTIVE";
        d.createdAt = Instant.now();
        d.updatedAt = Instant.now();
        return d;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isLicenseExpiringSoon() {
        return licenseExpiry != null && licenseExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    /** Always false if a PrDP isn't required for this driver — nothing to expire. */
    public boolean isPrdpExpiringSoon() {
        return prdpRequired && prdpExpiry != null && prdpExpiry.isBefore(LocalDate.now().plusDays(30));
    }

    public void update(String firstName, String lastName, String phone, String email, String idNumber,
                       String licenseNumber, String licenseCode, LocalDate licenseExpiry,
                       boolean prdpRequired, String prdpNumber, String prdpCategory,
                       LocalDate prdpExpiry, String notes) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.email = email;
        this.idNumber = idNumber;
        this.licenseNumber = licenseNumber;
        this.licenseCode = licenseCode;
        this.licenseExpiry = licenseExpiry;
        this.prdpRequired = prdpRequired;
        this.prdpNumber = prdpNumber;
        this.prdpCategory = prdpCategory;
        this.prdpExpiry = prdpExpiry;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
