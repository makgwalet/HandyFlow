// security/domain/model/GuardCompetency.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A skill or certification a guard holds: competency, evidence (certificate files in the evidence module),
 * issue and expiry dates and who verified it. Editing a competency removes the earlier verification, because
 * the verifier checked different details. Soft delete only.
 */
@Entity
@Table(name = "security_guard_competencies")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardCompetency {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "competency_type", nullable = false, length = 40)
    private Type type;

    @Column(length = 200)
    private String title;

    @Column(name = "issued_by", length = 200)
    private String issuedBy;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "certificate_ref", length = 200)
    private String certificateRef;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 1000)
    private String notes;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_by_name", length = 200)
    private String verifiedByName;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_note", length = 500)
    private String verificationNote;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public enum Type {
        FIREARM_COMPETENCY, FIRST_AID, FIREFIGHTING, DRIVER, CLOSE_PROTECTION, VIP_PROTECTION,
        CONTROL_ROOM, CCTV, ACCESS_CONTROL, CANINE, MINING_SECURITY, TACTICAL_RESPONSE, OTHER
    }

    public static GuardCompetency create(TenantId tenantId, UUID guardId, Type type, String title, String issuedBy,
                                         LocalDate issueDate, LocalDate expiryDate, String certificateRef,
                                         boolean required, String notes, UUID createdBy) {
        GuardCompetency c = new GuardCompetency();
        c.tenantId  = tenantId;
        c.guardId   = guardId;
        c.createdBy = createdBy;
        c.createdAt = Instant.now();
        c.apply(type, title, issuedBy, issueDate, expiryDate, certificateRef, required, notes);
        return c;
    }

    /** Edit the details. Any edit removes the earlier verification. */
    public void update(Type type, String title, String issuedBy, LocalDate issueDate, LocalDate expiryDate,
                       String certificateRef, boolean required, String notes) {
        apply(type, title, issuedBy, issueDate, expiryDate, certificateRef, required, notes);
        clearVerification();
    }

    private void apply(Type type, String title, String issuedBy, LocalDate issueDate, LocalDate expiryDate,
                       String certificateRef, boolean required, String notes) {
        if (expiryDate != null && issueDate != null && expiryDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before the issue date");
        }
        this.type           = type;
        this.title          = blankToNull(title);
        this.issuedBy       = blankToNull(issuedBy);
        this.issueDate      = issueDate;
        this.expiryDate     = expiryDate;
        this.certificateRef = blankToNull(certificateRef);
        this.required       = required;
        this.notes          = blankToNull(notes);
        this.updatedAt      = Instant.now();
    }

    public void verify(UUID by, String byName, String note) {
        this.verifiedBy        = by;
        this.verifiedByName    = byName;
        this.verifiedAt        = Instant.now();
        this.verificationNote  = blankToNull(note);
        this.updatedAt         = Instant.now();
    }

    private void clearVerification() {
        this.verifiedBy = null;
        this.verifiedByName = null;
        this.verifiedAt = null;
        this.verificationNote = null;
    }

    public void softDelete(UUID by) {
        if (deletedAt != null) throw new IllegalStateException("Competency already removed");
        this.deletedAt = Instant.now();
        this.deletedBy = by;
        this.updatedAt = Instant.now();
    }

    public boolean isVerified() { return verifiedAt != null; }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
