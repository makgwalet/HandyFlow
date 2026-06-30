// security/domain/model/Armoury.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Armoury — a single licensed firearm the company owns.
 *
 * This is the asset record, distinct from any one shift or checkout event.
 * security_armoury_logs (ArmouryLog) records the issue/return history;
 * this entity tracks the firearm's compliance lifecycle: SAPS license
 * number and expiry, service schedule, and current assignment status.
 *
 * The assignedGuardId field is a denormalized convenience kept in sync by
 * ArmouryService on every issue/return — the logs table is the source of
 * truth for "who held this firearm when," this field answers "who has it
 * right now" without scanning history.
 *
 * Status lifecycle:
 *   IN_ARMOURY    → available, not currently issued
 *   ISSUED        → currently assigned to a guard
 *   LOST          → reported lost/stolen — terminal until manually resolved
 *   DECOMMISSIONED → permanently retired from service
 */
@Entity
@Table(name = "security_armoury")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Armoury {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "firearm_serial", nullable = false, length = 100)
    private String firearmSerial;

    @Column(name = "firearm_type", nullable = false, length = 50)
    private String firearmType;

    @Column(name = "make_model", length = 150)
    private String makeModel;

    @Column(name = "saps_license_number", nullable = false, length = 100)
    private String sapsLicenseNumber;

    @Column(name = "license_issued_at")
    private LocalDate licenseIssuedAt;

    @Column(name = "license_expiry", nullable = false)
    private LocalDate licenseExpiry;

    @Column(name = "assigned_guard_id")
    private UUID assignedGuardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArmouryStatus status = ArmouryStatus.IN_ARMOURY;

    @Column(name = "last_service_at")
    private LocalDate lastServiceAt;

    @Column(name = "next_service_due_at")
    private LocalDate nextServiceDueAt;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static Armoury register(TenantId tenantId, String firearmSerial,
                                   String firearmType, String makeModel,
                                   String sapsLicenseNumber, LocalDate licenseIssuedAt,
                                   LocalDate licenseExpiry, String notes) {
        Armoury a            = new Armoury();
        a.tenantId           = tenantId;
        a.firearmSerial      = firearmSerial.strip();
        a.firearmType        = firearmType;
        a.makeModel          = makeModel;
        a.sapsLicenseNumber  = sapsLicenseNumber.strip();
        a.licenseIssuedAt    = licenseIssuedAt;
        a.licenseExpiry      = licenseExpiry;
        a.status             = ArmouryStatus.IN_ARMOURY;
        a.notes              = notes;
        a.createdAt          = Instant.now();
        a.updatedAt          = Instant.now();
        return a;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    /** Called by ArmouryService.issue() after the witnessed log entry is recorded. */
    public void markIssued(UUID guardId) {
        if (this.status != ArmouryStatus.IN_ARMOURY) {
            throw new IllegalStateException(
                    "Cannot issue firearm in status " + this.status);
        }
        this.assignedGuardId = guardId;
        this.status          = ArmouryStatus.ISSUED;
        this.updatedAt       = Instant.now();
    }

    /** Called by ArmouryService.returnFirearm() after the witnessed log entry is recorded. */
    public void markReturned() {
        if (this.status != ArmouryStatus.ISSUED) {
            throw new IllegalStateException(
                    "Cannot return firearm in status " + this.status);
        }
        this.assignedGuardId = null;
        this.status          = ArmouryStatus.IN_ARMOURY;
        this.updatedAt       = Instant.now();
    }

    public void reportLost(String notes) {
        this.status    = ArmouryStatus.LOST;
        this.notes     = appendNote(this.notes, notes);
        this.updatedAt = Instant.now();
    }

    public void decommission(String reason) {
        if (this.status == ArmouryStatus.ISSUED) {
            throw new IllegalStateException(
                    "Cannot decommission a firearm that is currently ISSUED — return it first");
        }
        this.status    = ArmouryStatus.DECOMMISSIONED;
        this.notes     = appendNote(this.notes, reason);
        this.updatedAt = Instant.now();
    }

    public void recordService(LocalDate serviceDate, LocalDate nextDueDate) {
        this.lastServiceAt     = serviceDate;
        this.nextServiceDueAt  = nextDueDate;
        this.updatedAt         = Instant.now();
    }

    public void updateLicense(String sapsLicenseNumber, LocalDate issuedAt, LocalDate expiry) {
        this.sapsLicenseNumber = sapsLicenseNumber.strip();
        this.licenseIssuedAt   = issuedAt;
        this.licenseExpiry     = expiry;
        this.updatedAt         = Instant.now();
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isAvailableForIssue() { return status == ArmouryStatus.IN_ARMOURY; }

    public boolean isLicenseExpired() {
        return licenseExpiry.isBefore(LocalDate.now());
    }

    private static String appendNote(String existing, String addition) {
        if (addition == null || addition.isBlank()) return existing;
        return existing == null || existing.isBlank()
                ? addition
                : existing + "\n" + addition;
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum ArmouryStatus {
        IN_ARMOURY, ISSUED, LOST, DECOMMISSIONED
    }
}
