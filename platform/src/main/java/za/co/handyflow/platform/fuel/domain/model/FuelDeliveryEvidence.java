package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One piece of proof of delivery. Records are never edited or deleted. A mistaken record is superseded (with a reason)
 * by a new one, until someone else verifies it, after which it is locked for good.
 */
@Entity
@Table(name = "fuel_delivery_evidence")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDeliveryEvidence {

    public static final Set<String> KINDS = Set.of("PHOTO", "SIGNATURE", "DOCUMENT", "GPS");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(nullable = false, length = 12)
    private String kind;

    @Column(name = "file_ref", length = 500)
    private String fileRef;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_m", precision = 8, scale = 2)
    private BigDecimal accuracyM;

    @Column(length = 300)
    private String note;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();

    @Column(name = "captured_by")
    private UUID capturedBy;

    @Column(nullable = false, length = 12)
    private String status = "ACTIVE";

    @Column(name = "superseded_reason", length = 300)
    private String supersededReason;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Version
    private Long version;

    public static FuelDeliveryEvidence create(TenantId tenantId, UUID deliveryId, String kind, String fileRef,
                                              BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyM, String note, UUID by) {
        if (kind == null || !KINDS.contains(kind)) throw new IllegalArgumentException("Evidence type must be one of PHOTO, SIGNATURE, DOCUMENT or GPS.");
        FuelDeliveryEvidence e = new FuelDeliveryEvidence();
        e.tenantId = tenantId; e.deliveryId = deliveryId; e.kind = kind; e.capturedBy = by;
        if ("GPS".equals(kind)) {
            if (latitude == null || longitude == null) throw new IllegalArgumentException("A GPS record needs a latitude and a longitude.");
            if (latitude.abs().compareTo(new BigDecimal("90")) > 0) throw new IllegalArgumentException("Latitude must be between -90 and 90.");
            if (longitude.abs().compareTo(new BigDecimal("180")) > 0) throw new IllegalArgumentException("Longitude must be between -180 and 180.");
            if (accuracyM != null && accuracyM.signum() < 0) throw new IllegalArgumentException("Accuracy cannot be negative.");
            e.latitude = latitude; e.longitude = longitude; e.accuracyM = accuracyM;
        } else {
            if (fileRef == null || fileRef.isBlank()) throw new IllegalArgumentException("Attach the file for this evidence.");
            e.fileRef = fileRef.trim().length() > 500 ? fileRef.trim().substring(0, 500) : fileRef.trim();
        }
        e.note = note == null || note.isBlank() ? null : (note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim());
        return e;
    }

    public boolean isVerified() { return verifiedAt != null; }
    public boolean isActive() { return "ACTIVE".equals(status); }

    /** A different person from the one who captured it must verify, and verifying locks the record. */
    public void verify(UUID by) {
        if (!isActive()) throw new IllegalStateException("This evidence has been replaced and cannot be verified.");
        if (isVerified()) return;
        if (by == null || by.equals(capturedBy)) throw new IllegalStateException("Evidence must be verified by someone other than the person who captured it.");
        this.verifiedBy = by; this.verifiedAt = Instant.now();
    }

    public void supersede(UUID by, String reason) {
        if (isVerified()) throw new IllegalStateException("Verified evidence is locked and cannot be replaced.");
        if (!isActive()) throw new IllegalStateException("This evidence has already been replaced.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = "SUPERSEDED"; this.supersededBy = by; this.supersededAt = Instant.now();
        this.supersededReason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
    }
}
