package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A cleaning of one compartment, with its certificate or evidence note. Append only: a mistake is corrected by recording another, never by editing. */
@Entity
@Table(name = "fuel_compartment_cleanings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelCompartmentCleaning {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "compartment_id", nullable = false, updatable = false)
    private UUID compartmentId;

    @Column(name = "cleaned_at", nullable = false, updatable = false)
    private Instant cleanedAt;

    @Column(length = 120, updatable = false)
    private String method;

    @Column(name = "certificate_ref", length = 120, updatable = false)
    private String certificateRef;

    @Column(name = "evidence_note", length = 300, updatable = false)
    private String evidenceNote;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "file_ref", length = 500) private String fileRef;
    @Column(name = "content_type", length = 100) private String contentType;
    @Column(name = "file_size_bytes") private Long fileSizeBytes;
    @Column(name = "file_name", length = 255) private String fileName;
    @Column(name = "file_uploaded_by") private UUID fileUploadedBy;
    @Column(name = "file_uploaded_at") private Instant fileUploadedAt;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_at") private Instant verifiedAt;

    public boolean hasFile() { return fileRef != null; }
    public boolean isVerified() { return verifiedAt != null; }
    /** Counts towards clearing a changeover when the company asks for verified evidence: the certificate is uploaded and someone else confirmed it. */
    public boolean isVerifiedEvidence() { return hasFile() && isVerified(); }

    /** One file per cleaning, kept as uploaded. A wrong file is corrected by recording another cleaning, never by replacing it. */
    public void attachFile(String key, String type, long size, String name, UUID by) {
        if (hasFile()) throw new IllegalStateException("This cleaning already has a certificate. Record a new cleaning to correct it.");
        if (key == null || key.isBlank() || type == null || size <= 0) throw new IllegalArgumentException("The file was not stored.");
        this.fileRef = key; this.contentType = type; this.fileSizeBytes = size;
        this.fileName = name == null ? null : (name.length() > 120 ? name.substring(0, 120) : name);
        this.fileUploadedBy = by; this.fileUploadedAt = Instant.now();
    }

    /** A second person confirms the certificate. Not the person who recorded the cleaning or uploaded the file. */
    public void verify(UUID by) {
        if (by == null) throw new IllegalStateException("You are not signed in as a user.");
        if (!hasFile()) throw new IllegalStateException("Upload the certificate before it can be verified.");
        if (isVerified()) throw new IllegalStateException("This certificate is already verified.");
        if (by.equals(recordedBy) || by.equals(fileUploadedBy)) throw new IllegalStateException("You recorded this cleaning or uploaded its certificate, so someone else must verify it.");
        this.verifiedBy = by; this.verifiedAt = Instant.now();
    }

    /** A cleaning needs its evidence: a certificate reference, a note, or both. It cannot be dated in the future. */
    public static FuelCompartmentCleaning record(TenantId tenantId, UUID compartmentId, Instant cleanedAt, String method, String certificateRef, String evidenceNote, UUID by, Instant now) {
        if (cleanedAt == null) throw new IllegalArgumentException("Give when the compartment was cleaned.");
        if (cleanedAt.isAfter(now.plusSeconds(300))) throw new IllegalArgumentException("A cleaning cannot be dated in the future.");
        String cert = trim(certificateRef, 120), note = trim(evidenceNote, 300);
        if (cert == null && note == null) throw new IllegalArgumentException("Give the cleaning certificate reference, or describe the evidence.");
        FuelCompartmentCleaning c = new FuelCompartmentCleaning();
        c.tenantId = tenantId; c.compartmentId = compartmentId; c.cleanedAt = cleanedAt; c.method = trim(method, 120);
        c.certificateRef = cert; c.evidenceNote = note; c.recordedBy = by;
        return c;
    }

    private static String trim(String v, int max) { return v == null || v.isBlank() ? null : (v.trim().length() > max ? v.trim().substring(0, max) : v.trim()); }
}
