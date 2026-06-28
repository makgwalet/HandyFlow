// security/domain/model/Guard.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Guard — a PSiRA-registered security officer employed by a tenant.
 *
 * Status workflow (distinct from the active tombstone):
 *
 *   ACTIVE              → ON_LEAVE / SUSPENDED / UNDER_INVESTIGATION / TERMINATED
 *   ON_LEAVE            → ACTIVE
 *   SUSPENDED           → ACTIVE / TERMINATED
 *   UNDER_INVESTIGATION → ACTIVE / SUSPENDED / TERMINATED
 *   TERMINATED          → (terminal — no way back, soft-delete separately)
 *
 * WHY separate status from active?
 * `active = false` means the record is logically deleted.
 * `status = SUSPENDED` means the guard exists and has a history but must not
 * be scheduled for shifts until reinstated.  Collapsing suspension and deletion
 * into the same boolean loses that distinction — you can't reinstate a deleted
 * record cleanly, and you can't tell from a deleted record whether it was
 * a disciplinary suspension or a normal resignation.
 */
@Entity
@Table(name = "security_guards")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Guard {

    private static final Set<String> SCHEDULABLE_STATUSES = Set.of("ACTIVE");
    private static final Set<String> VALID_STATUSES =
            Set.of("ACTIVE", "ON_LEAVE", "SUSPENDED", "UNDER_INVESTIGATION", "TERMINATED");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "psira_number")
    private String psiraNumber;

    @Column(name = "id_number")
    private String idNumber;

    private String phone;

    @Column(name = "photo_url")
    private String photoUrl;

    private String grade;

    @Column(nullable = false)
    private boolean active = true;

    private String notes;

    // ── Status workflow (added in V50) ────────────────────────────────────────

    /**
     * Operational status — independent of the soft-delete tombstone (deletedAt).
     * A guard can be ACTIVE in the DB but SUSPENDED for scheduling purposes.
     *
     * WHY DEFAULT 'ACTIVE'? All guards created before V50 were schedulable.
     * The DB default keeps them schedulable after the migration.
     */
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_note")
    private String statusNote;

    /**
     * PSiRA registration expiry.
     * WHY track this?
     * The Sectoral Determination and PSiRA regulations require a valid
     * registration for a guard to be deployed.  Scheduling an expired guard
     * exposes the company to regulatory fines and criminal liability.
     * A future compliance check on createShift will block scheduling if this
     * date is in the past.
     */
    @Column(name = "psira_expiry_date")
    private LocalDate psiraExpiryDate;

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

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Guard create(TenantId tenantId, String firstName, String lastName,
                               String psiraNumber, String idNumber, String phone,
                               String grade) {
        Guard g = new Guard();
        g.tenantId    = tenantId;
        g.firstName   = firstName.trim();
        g.lastName    = lastName.trim();
        g.psiraNumber = psiraNumber;
        g.idNumber    = idNumber;
        g.phone       = phone;
        g.grade       = grade;
        g.status      = "ACTIVE";
        g.active      = true;
        g.createdAt   = Instant.now();
        g.updatedAt   = Instant.now();
        return g;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void update(String firstName, String lastName, String psiraNumber,
                       String idNumber, String phone, String grade, String notes) {
        this.firstName   = firstName.trim();
        this.lastName    = lastName.trim();
        this.psiraNumber = psiraNumber;
        this.idNumber    = idNumber;
        this.phone       = phone;
        this.grade       = grade;
        this.notes       = notes;
        this.updatedAt   = Instant.now();
    }

    /**
     * Updates guard status with an audit trail.
     *
     * WHY require a note for SUSPENDED and TERMINATED?
     * These are serious HR/legal actions.  A status change with no explanation
     * is worthless in a disciplinary hearing or a labour dispute.  The service
     * layer enforces the note requirement; the domain just records it.
     *
     * WHY set active=false for TERMINATED?
     * TERMINATED is a permanent end-state.  The guard should not be listed in
     * active roster queries.  They remain in the DB for audit/history purposes
     * (shifts, incidents, checkpoint logs retain the foreign key).
     */
    public void updateStatus(String newStatus, String note, UUID changedBy) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid guard status: " + newStatus);
        }
        this.status          = newStatus;
        this.statusNote      = note;
        this.statusChangedAt = Instant.now();
        if ("TERMINATED".equals(newStatus)) {
            this.active = false;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Whether this guard can be assigned to a new shift.
     * Called by ShiftService.createShift before saving.
     */
    public boolean isSchedulable() {
        return SCHEDULABLE_STATUSES.contains(this.status) && this.active && this.deletedAt == null;
    }

    /**
     * Photo update for dev mode.
     *
     * WHY accept base64 here in dev but warn?
     * We don't have S3 yet.  In production, the service layer should upload
     * to object storage and pass only the CDN URL here.  The domain method
     * accepts any string so the interface is correct for prod; the service
     * layer is responsible for rejecting raw base64 when S3 is available.
     *
     * For now: if the value is a data URI (starts with "data:"), we store a
     * placeholder token ("PENDING_UPLOAD:<first 20 chars of hash>") and log a
     * warning so we can see the pattern but don't bloat the DB row.
     * The frontend still shows the last-uploaded photo from the GuardResponse
     * (which will be null for new captures until S3 is wired up).
     */
    public void updatePhoto(String photoUrlOrBase64) {
        if (photoUrlOrBase64 != null && photoUrlOrBase64.startsWith("data:")) {
            // Dev-mode guard: store a flag, not the full base64 blob.
            // WHY? A base64-encoded 1MB photo becomes ~1.33MB of text.
            // Storing it in a VARCHAR column bloats every SELECT on this
            // row, kills the GuardResponse payload, and can't be CDN-cached.
            // In prod: upload to S3 → store the CDN URL only.
            this.photoUrl  = "PENDING_UPLOAD";
        } else {
            this.photoUrl = photoUrlOrBase64;
        }
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
