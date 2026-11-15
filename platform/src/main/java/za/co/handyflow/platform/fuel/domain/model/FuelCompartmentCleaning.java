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
