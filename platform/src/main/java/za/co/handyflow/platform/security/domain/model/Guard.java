// security/domain/model/Guard.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Guard — CHANGE (V214): added employeeCode + assignEmployeeCode(). See the
 * V214 migration for the full rationale (globally-unique, tenant-prefixed
 * login identifier for guards without a reliable phone number). Everything
 * else below is unchanged from the original.
 */
@Entity
@Table(name = "security_guards")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Guard {

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

    // ── Operational status (Phase 1.5) ─────────────────────────────────────────
    // ACTIVE | ON_LEAVE | SUSPENDED | UNDER_INVESTIGATION | TERMINATED

    @Column(name = "status", length = 30)
    private String status = "ACTIVE";

    @Column(name = "status_note")
    private String statusNote;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    // ── PSiRA compliance ───────────────────────────────────────────────────────

    @Column(name = "psira_expiry_date")
    private LocalDate psiraExpiryDate;

    // ── Phase 1.5: PIN lifecycle ───────────────────────────────────────────────

    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "pin_changed_at")
    private Instant pinChangedAt;

    @Column(name = "pin_expires_at")
    private Instant pinExpiresAt;

    @Column(name = "pin_must_change")
    private boolean pinMustChange = false;

    @Column(name = "pin_failed_attempts")
    private int pinFailedAttempts = 0;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "pin_history", columnDefinition = "TEXT")
    private String pinHistory;   // JSON array of last N hashed PINs

    @Column(name = "registered_device_id")
    private String registeredDeviceId;

    @Column(name = "face_embedding", columnDefinition = "TEXT")
    private String faceEmbedding;

    // ── Screening ──────────────────────────────────────────────────────────────

    @Column(name = "screening_status", length = 20)
    private String screeningStatus = "UNSCREENED";

    // ── Firearms ───────────────────────────────────────────────────────────────

    @Column(name = "firearm_competency_number", length = 50)
    private String firearmCompetencyNumber;

    @Column(name = "firearm_competency_expiry")
    private LocalDate firearmCompetencyExpiry;

    // ── Phase 3 Part 9.5: CP vetting tier ─────────────────────────────────────

    @Column(name = "cp_vetting_tier", length = 20)
    private String cpVettingTier;

    @Column(name = "cp_vetting_cleared_at")
    private LocalDate cpVettingClearedAt;

    @Column(name = "cp_vetting_expires_at")
    private LocalDate cpVettingExpiresAt;

    // ── Phase 4: Payroll rate ──────────────────────────────────────────────────

    @Column(name = "hourly_rate_cents")
    private Integer hourlyRateCents;

    @Column(name = "rate_effective_from")
    private LocalDate rateEffectiveFrom;

    // ── Phase 4: Branch scoping ────────────────────────────────────────────────

    @Column(name = "primary_branch_id")
    private UUID primaryBranchId;

    // ── V214: Employee code (guard onboarding) ─────────────────────────────────

    @Column(name = "employee_code", unique = true, length = 20)
    private String employeeCode;

    // ── V220: Emergency contact ──────────────────────────────────────────────
    // At least one of guard.phone / emergencyContactPhone is required at
    // creation (enforced in GuardService, not here) -- see V220 migration.

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static Guard create(TenantId tenantId, String firstName, String lastName,
                               String psiraNumber, String idNumber, String phone,
                               String grade, LocalDate psiraExpiryDate,
                               String emergencyContactName, String emergencyContactPhone) {
        Guard g = new Guard();
        g.tenantId        = tenantId;
        g.firstName       = firstName.trim();
        g.lastName        = lastName.trim();
        g.psiraNumber     = psiraNumber;
        g.idNumber        = idNumber;
        g.phone           = phone;
        g.grade           = grade;
        g.psiraExpiryDate = psiraExpiryDate;
        g.emergencyContactName  = emergencyContactName;
        g.emergencyContactPhone = emergencyContactPhone;
        g.active          = true;
        g.status          = "ACTIVE";
        g.createdAt       = Instant.now();
        g.updatedAt       = Instant.now();
        return g;
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public String getFullName() { return firstName + " " + lastName; }

    public boolean isDeleted()  { return deletedAt != null; }

    public boolean isSchedulable() {
        return active && deletedAt == null
                && "ACTIVE".equals(status)
                && !"FLAGGED".equals(screeningStatus);
    }

    public boolean isPinLocked() {
        return pinLockedUntil != null && Instant.now().isBefore(pinLockedUntil);
    }

    public boolean isPinExpired() {
        return pinExpiresAt != null && Instant.now().isAfter(pinExpiresAt);
    }

    public boolean isPinMustChange() { return pinMustChange; }

    public boolean hasFirearmCompetency() {
        return firearmCompetencyExpiry != null
                && !firearmCompetencyExpiry.isBefore(LocalDate.now());
    }

    public boolean meetsVettingTierFor(String principalThreatLevel) {
        if (principalThreatLevel == null) return true;
        if (cpVettingExpiresAt != null && cpVettingExpiresAt.isBefore(LocalDate.now())) return false;
        return tierRank(cpVettingTier) >= tierRank(principalThreatLevel);
    }

    private static int tierRank(String tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case "LOW", "STANDARD"    -> 1;
            case "MEDIUM", "ENHANCED" -> 2;
            case "HIGH"               -> 3;
            case "CRITICAL"           -> 4;
            default                   -> 0;
        };
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void update(String firstName, String lastName, String psiraNumber,
                       String idNumber, String phone, String grade, String notes,
                       LocalDate psiraExpiryDate, String emergencyContactName,
                       String emergencyContactPhone) {
        this.firstName       = firstName.trim();
        this.lastName        = lastName.trim();
        this.psiraNumber     = psiraNumber;
        this.idNumber        = idNumber;
        this.phone           = phone;
        this.grade           = grade;
        this.notes           = notes;
        this.psiraExpiryDate = psiraExpiryDate;
        this.emergencyContactName  = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.updatedAt       = Instant.now();
    }

    public void updatePhoto(String photoUrl) {
        this.photoUrl  = photoUrl;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(String status, String note, UUID changedBy) {
        this.status          = status;
        this.statusNote      = note;
        this.statusChangedAt = Instant.now();
        this.statusChangedBy = changedBy;
        if (!"ACTIVE".equals(status)) this.active = false;
        if ("ACTIVE".equals(status))  this.active = true;
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

    // ── PIN lifecycle ──────────────────────────────────────────────────────────

    public void setPinHash(String pinHash, Instant expiresAt) {
        this.pinHash         = pinHash;
        this.pinExpiresAt    = expiresAt;
        this.pinChangedAt    = Instant.now();
        this.pinMustChange   = true;
        this.pinFailedAttempts = 0;
        this.pinLockedUntil  = null;
        this.updatedAt       = Instant.now();
    }

    public void updatePinWithHistory(String newPinHash) {
        this.pinHash         = newPinHash;
        this.pinChangedAt    = Instant.now();
        this.pinMustChange   = false;
        this.pinFailedAttempts = 0;
        this.pinLockedUntil  = null;
        this.updatedAt       = Instant.now();
    }

    public void clearPinMustChange() {
        this.pinMustChange = false;
        this.updatedAt     = Instant.now();
    }

    public boolean recordPinFailure() {
        this.pinFailedAttempts++;
        if (this.pinFailedAttempts >= 5) {
            this.pinLockedUntil = Instant.now().plusSeconds(15 * 60);
        }
        this.updatedAt = Instant.now();
        return isPinLocked();
    }

    public void recordPinSuccess() {
        this.pinFailedAttempts = 0;
        this.pinLockedUntil    = null;
        this.updatedAt         = Instant.now();
    }

    public void setFaceEmbedding(String embedding) {
        this.faceEmbedding = embedding;
        this.updatedAt     = Instant.now();
    }

    public void setRegisteredDeviceId(String deviceId) {
        this.registeredDeviceId = deviceId;
        this.updatedAt          = Instant.now();
    }

    // ── Screening ──────────────────────────────────────────────────────────────

    public void setScreeningStatus(String status) {
        this.screeningStatus = status;
        this.updatedAt       = Instant.now();
    }

    // ── Firearms ───────────────────────────────────────────────────────────────

    public void setFirearmCompetency(String competencyNumber, LocalDate expiry) {
        this.firearmCompetencyNumber = competencyNumber;
        this.firearmCompetencyExpiry = expiry;
        this.updatedAt               = Instant.now();
    }

    // ── CP vetting ─────────────────────────────────────────────────────────────

    public void setCpVettingTier(String tier, LocalDate clearedAt, LocalDate expiresAt) {
        this.cpVettingTier      = tier;
        this.cpVettingClearedAt = clearedAt;
        this.cpVettingExpiresAt = expiresAt;
        this.updatedAt          = Instant.now();
    }

    // ── Phase 4: Payroll & Branch ──────────────────────────────────────────────

    public void setHourlyRate(int hourlyRateCents, LocalDate effectiveFrom) {
        this.hourlyRateCents   = hourlyRateCents;
        this.rateEffectiveFrom = effectiveFrom;
        this.updatedAt         = Instant.now();
    }

    public void setPrimaryBranch(UUID branchId) {
        this.primaryBranchId = branchId;
        this.updatedAt       = Instant.now();
    }

    // ── V214: Employee code ──────────────────────────────────────────────────

    /**
     * Assigns the tenant-prefixed employee code generated by
     * GuardService.generateEmployeeCode(). Set once at guard creation and
     * never reassigned -- see V214 migration comment on why the unique
     * index doesn't exclude soft-deleted guards (a retired code must never
     * be reissued).
     */
    public void assignEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
        this.updatedAt     = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}