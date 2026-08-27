// security/domain/model/GateRegisterEntry.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * GateRegisterEntry — one visit through an AccessPoint. Mirrors
 * DeviceSession's own open/close shape deliberately (one row per visit,
 * loggedOutAt == null means still on site) — same reasoning DeviceSession
 * itself uses: simplest way to answer "who is currently on site" is a
 * single row you either have or don't, not separate paired IN/OUT rows.
 * <p>
 * loggedInByGuardId/loggedOutByGuardId are resolved server-side from the
 * authenticated guard's open DeviceSession (DeviceSessionService.
 * resolveGuardId()) — same identity-spoofing fix already applied to
 * CheckpointScanController's guardId, for the identical reason. Never
 * trust a client-supplied guard field.
 * <p>
 * No photoUrl column — deliberately superseding the original plan's
 * own §4 sketch, which specified the legacy CpEvidence-style base64
 * pattern. Photos/documents (ID scan, license disc, general evidence)
 * attach via the shared evidence module's EvidenceFacade instead,
 * queried separately by entityId — supports multiple attachments per
 * entry naturally, which a single photoUrl column never could.
 */
@Entity
@Table(name = "security_gate_register_entries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GateRegisterEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "access_point_id", nullable = false)
    private UUID accessPointId;

    @Column(name = "entry_type", nullable = false, length = 30)
    private String entryType;   // VISITOR | CONTRACTOR | DELIVERY | STAFF_VEHICLE | OTHER

    // ── Person ─────────────────────────────────────────────────────────────────

    @Column(name = "person_name", nullable = false)
    private String personName;

    /**
     * Stored raw — masking (same convention as Guard.idNumber via
     * GuardService.maskIdNumber()) happens at the response-mapping
     * layer on every read path, not here. Encryption-at-rest via
     * FieldEncryptionService is a real, deliberate open question — see
     * this feature's own design notes; NOT applied by default without
     * an explicit decision.
     */
    @Column(name = "id_number")
    private String idNumber;

    private String phone;
    private String company;

    // ── Visit context ─────────────────────────────────────────────────────────

    @Column(name = "host_name")
    private String hostName;

    @Column(name = "host_contact")
    private String hostContact;

    private String purpose;

    // ── Vehicle (optional) ───────────────────────────────────────────────────

    @Column(name = "vehicle_registration")
    private String vehicleRegistration;

    @Column(name = "vehicle_make_model")
    private String vehicleMakeModel;

    @Column(name = "driver_name")
    private String driverName;

    /**
     * FIX: mobile addendum. Lets a supervisor reviewing the log later
     * tell how trustworthy this entry's identity data actually is — an
     * operational requirement given this data may get reviewed after a
     * real incident, not a nice-to-have. Only meaningful for the ID
     * document, not the vehicle disc (discs are OCR-or-manual only, no
     * barcode path exists for them at all) — null is valid when no ID
     * scan was attempted (e.g. a delivery entry with no personal ID
     * captured).
     */
    @Column(name = "id_scan_confidence", length = 20)
    private String idScanConfidence;   // BARCODE_DECODED | OCR_EXTRACTED | MANUAL_ENTRY

    // ── Identity of the logging guard(s) — session-resolved, never client-supplied ──

    @Column(name = "logged_in_by_guard_id", nullable = false)
    private UUID loggedInByGuardId;

    @Column(name = "logged_in_at", nullable = false, updatable = false)
    private Instant loggedInAt;

    @Column(name = "logged_out_by_guard_id")
    private UUID loggedOutByGuardId;

    @Column(name = "logged_out_at")
    private Instant loggedOutAt;

    // ── Phase B (nullable now, wired later) ──────────────────────────────────

    @Column(name = "linked_pre_registration_id")
    private UUID linkedPreRegistrationId;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Column(nullable = false, length = 20)
    private String status = "ON_SITE";   // ON_SITE | DEPARTED | OVERSTAYED

    /**
     * Dedup column for the overstay scheduler — same mark-before-send
     * convention as Shift.overstayAlertSentAt / NoShowAlertScheduler.
     */
    @Column(name = "overstay_alert_sent_at")
    private Instant overstayAlertSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static GateRegisterEntry logArrival(TenantId tenantId, UUID siteId, UUID accessPointId,
                                               String entryType, String personName, String idNumber,
                                               String phone, String company,
                                               String hostName, String hostContact, String purpose,
                                               String vehicleRegistration, String vehicleMakeModel,
                                               String driverName, String idScanConfidence,
                                               UUID loggedInByGuardId) {
        GateRegisterEntry e     = new GateRegisterEntry();
        e.tenantId              = tenantId;
        e.siteId                = siteId;
        e.accessPointId         = accessPointId;
        e.entryType             = entryType;
        e.personName            = personName;
        e.idNumber              = idNumber;
        e.phone                 = phone;
        e.company                = company;
        e.hostName              = hostName;
        e.hostContact           = hostContact;
        e.purpose               = purpose;
        e.vehicleRegistration   = vehicleRegistration;
        e.vehicleMakeModel      = vehicleMakeModel;
        e.driverName            = driverName;
        e.idScanConfidence      = idScanConfidence;
        e.loggedInByGuardId     = loggedInByGuardId;
        e.loggedInAt            = Instant.now();
        e.status                = "ON_SITE";
        e.createdAt             = Instant.now();
        e.updatedAt             = Instant.now();
        return e;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void logExit(UUID loggedOutByGuardId) {
        if (!"ON_SITE".equals(status) && !"OVERSTAYED".equals(status)) {
            throw new IllegalStateException("Only an ON_SITE or OVERSTAYED entry can be logged as departed");
        }
        this.loggedOutByGuardId = loggedOutByGuardId;
        this.loggedOutAt        = Instant.now();
        this.status             = "DEPARTED";
        this.updatedAt          = Instant.now();
    }

    /**
     * Called by the overstay scheduler when an entry is still ON_SITE
     * past the configured threshold. Deliberately does not touch
     * overstayAlertSentAt itself — see markOverstayAlertSent() below,
     * called separately so a status transition and a
     * notification-already-sent mark stay independently testable and
     * independently idempotent, matching Shift's own separation of
     * miss()/markNoShowAlertSent().
     */
    public void markOverstayed() {
        if (!"ON_SITE".equals(status)) {
            throw new IllegalStateException("Only an ON_SITE entry can be marked OVERSTAYED");
        }
        this.status    = "OVERSTAYED";
        this.updatedAt = Instant.now();
    }

    /** Mark-before-send dedup, same convention as Shift.markNoShowAlertSent(). */
    public void markOverstayAlertSent() {
        this.overstayAlertSentAt = Instant.now();
    }

    public boolean isOnSite() {
        return "ON_SITE".equals(status) || "OVERSTAYED".equals(status);
    }
}