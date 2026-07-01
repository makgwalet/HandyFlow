// security/domain/model/Principal.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Principal — the person being protected in a close-protection engagement.
 *
 * Distinct from Guard (who provides protection) and Site (no fixed location
 * applies — a principal moves between office, residence, restaurant, airport
 * in one day; see Part 9.1).
 *
 * Confidentiality (Part 9.3):
 * Codenames (aliasCodename) are used everywhere a principal would otherwise
 * be named — team comms, dashboards, push notifications — so a glanced-at
 * phone screen doesn't expose who is being protected. Real identity is only
 * resolvable through this entity by someone with VIP_DETAIL_ACCESS.
 *
 * medicalNotes/knownThreats are stored ENCRYPTED (AES-256-GCM) at rest as
 * of Part 9.3 — but encryption/decryption happens at the CloseProtectionService
 * boundary, not in this entity. This entity's fields always hold ciphertext
 * when read from/written to the database; the service decrypts before
 * building a PrincipalResponse and encrypts before persisting. Keeping the
 * entity itself encryption-agnostic matches the rest of this codebase's
 * pattern of thin entities with business logic in the service layer, and
 * means a future switch to per-tenant keys or a different cipher only
 * touches CloseProtectionService + FieldEncryptionService, not this class.
 */
@Entity
@Table(name = "security_principals")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Principal {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "alias_codename", nullable = false, length = 50)
    private String aliasCodename;

    @Enumerated(EnumType.STRING)
    @Column(name = "threat_level", nullable = false, length = 20)
    private ThreatLevel threatLevel = ThreatLevel.LOW;

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(name = "known_threats", columnDefinition = "TEXT")
    private String knownThreats;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "emergency_contacts", columnDefinition = "jsonb")
    private String emergencyContacts;   // JSON array of {name, relationship, phone}

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static Principal create(TenantId tenantId, String fullName, String aliasCodename,
                                   ThreatLevel threatLevel, String medicalNotes,
                                   String knownThreats, String emergencyContacts) {
        Principal p          = new Principal();
        p.tenantId           = tenantId;
        p.fullName           = fullName.strip();
        p.aliasCodename      = aliasCodename.strip();
        p.threatLevel        = threatLevel != null ? threatLevel : ThreatLevel.LOW;
        p.medicalNotes       = medicalNotes;
        p.knownThreats       = knownThreats;
        p.emergencyContacts  = emergencyContacts;
        p.active             = true;
        p.createdAt          = Instant.now();
        p.updatedAt          = Instant.now();
        return p;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void update(String fullName, String aliasCodename, ThreatLevel threatLevel,
                       String medicalNotes, String knownThreats, String emergencyContacts) {
        this.fullName          = fullName.strip();
        this.aliasCodename     = aliasCodename.strip();
        this.threatLevel       = threatLevel;
        this.medicalNotes      = medicalNotes;
        this.knownThreats      = knownThreats;
        this.emergencyContacts = emergencyContacts;
        this.updatedAt         = Instant.now();
    }

    public void setPhotoUrl(String url) {
        this.photoUrl  = url;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum ThreatLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
