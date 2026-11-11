package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/** A licence, calibration, inspection, certificate or insurance that expires, held against a depot, tank, tanker, driver or the company. */
@Entity
@Table(name = "fuel_compliance_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelComplianceItem {

    public static final Set<String> TYPES = Set.of("LICENCE", "CALIBRATION", "INSPECTION", "CERTIFICATE", "INSURANCE", "OTHER");
    public static final Set<String> SUBJECTS = Set.of("COMPANY", "DEPOT", "TANK", "TANKER", "DRIVER", "OTHER");
    /** Days before expiry that an item is flagged as expiring. */
    public static final int WARN_DAYS = 30;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "reference_no", length = 80)
    private String referenceNo;

    @Column(name = "subject_type", nullable = false, updatable = false, length = 12)
    private String subjectType;

    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    @Column(name = "subject_label", length = 120)
    private String subjectLabel;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelComplianceItem create(TenantId tenantId, String type, String title, String referenceNo, String subjectType,
                                            UUID subjectId, String subjectLabel, LocalDate issuedOn, LocalDate expiresOn, String notes) {
        if (subjectType == null || !SUBJECTS.contains(subjectType)) throw new IllegalArgumentException("Unknown subject: " + subjectType);
        if (!"COMPANY".equals(subjectType) && !"OTHER".equals(subjectType) && subjectId == null) {
            throw new IllegalArgumentException("Choose which " + subjectType.toLowerCase() + " this is for.");
        }
        FuelComplianceItem i = new FuelComplianceItem();
        i.tenantId = tenantId; i.subjectType = subjectType; i.subjectId = subjectId; i.subjectLabel = subjectLabel; i.createdAt = Instant.now();
        i.update(type, title, referenceNo, issuedOn, expiresOn, notes);
        return i;
    }

    public void update(String type, String title, String referenceNo, LocalDate issuedOn, LocalDate expiresOn, String notes) {
        if (type == null || !TYPES.contains(type)) throw new IllegalArgumentException("Unknown type: " + type);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Give it a title.");
        if (expiresOn == null) throw new IllegalArgumentException("Give the expiry date.");
        if (issuedOn != null && expiresOn.isBefore(issuedOn)) throw new IllegalArgumentException("The expiry date is before the issue date.");
        this.itemType = type; this.title = title.trim(); this.referenceNo = blank(referenceNo);
        this.issuedOn = issuedOn; this.expiresOn = expiresOn; this.notes = blank(notes); this.updatedAt = Instant.now();
    }

    /** Extends validity. The new expiry must be later than the current one: renewing cannot shorten a certificate. */
    public LocalDate renew(LocalDate newExpiry, LocalDate issued) {
        if (newExpiry == null || !newExpiry.isAfter(expiresOn)) throw new IllegalArgumentException("The new expiry must be later than the current one (" + expiresOn + ").");
        LocalDate previous = expiresOn;
        this.expiresOn = newExpiry; if (issued != null) this.issuedOn = issued; this.updatedAt = Instant.now();
        return previous;
    }

    public String statusOn(LocalDate today) {
        if (expiresOn.isBefore(today)) return "EXPIRED";
        if (ChronoUnit.DAYS.between(today, expiresOn) <= WARN_DAYS) return "EXPIRING";
        return "VALID";
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = Instant.now(); }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
