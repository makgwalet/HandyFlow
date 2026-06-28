package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Checkpoint — a physical scan point within a Site.
 *
 * Supports three verification methods:
 *   QR  — a unique UUID-based QR payload, printable and mountable on a wall.
 *   NFC — an NFC tag UID, typically a passive NTAG213/215 sticker.
 *   BLE — a Bluetooth Low Energy beacon identifier.
 *
 * WHY multiple methods per checkpoint?
 * Different site environments favour different hardware.  A server room with
 * no camera coverage and good phone signal uses QR.  A mine shaft with
 * interference uses NFC.  A large perimeter where guards walk past rather than
 * stop uses BLE (proximity detection without needing to physically tap).
 * A single checkpoint can have all three set — the scan service accepts
 * whichever the guard's device supports and routes accordingly.
 *
 * Only one of (qrCode, nfcTagUid, bleBeaconId) needs to be non-null for a
 * checkpoint to be scannable.  The current QR-only scanners in the field
 * continue working unchanged; NFC/BLE fields are additive.
 */
@Entity
@Table(name = "security_checkpoints")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Checkpoint {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * QR code payload — a UUID string, unique across the system.
     * The guard app encodes this into a QR image; scanning returns this value.
     */
    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    /**
     * NFC tag UID — the hardware UID of the passive NFC sticker attached to
     * this checkpoint.  Read by the NFC controller on the guard's phone.
     * Null if NFC is not configured for this checkpoint.
     */
    @Column(name = "nfc_tag_uid")
    private String nfcTagUid;

    /**
     * BLE beacon identifier — the UUID broadcast by the BLE beacon at this
     * checkpoint.  The guard app detects this when within RSSI threshold.
     * Null if BLE is not configured for this checkpoint.
     */
    @Column(name = "ble_beacon_id")
    private String bleBeaconId;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Checkpoint create(TenantId tenantId, Site site,
                                    String name, String description,
                                    int sortOrder) {
        Checkpoint c = new Checkpoint();
        c.tenantId    = tenantId;
        c.site        = site;
        c.name        = name.trim();
        c.description = description;
        // WHY UUID as QR payload?
        // - Globally unique: no two checkpoints share a code even across tenants.
        // - Unguessable: a sequential ID could be incremented to probe other sites.
        // - Contains no site/tenant info that could be reverse-engineered.
        // Future: sign with site.qrSecret to add expiry and tamper-evidence (Phase 1).
        c.qrCode      = UUID.randomUUID().toString();
        c.sortOrder   = sortOrder;
        c.active      = true;
        c.createdAt   = Instant.now();
        c.updatedAt   = Instant.now();
        return c;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    /**
     * Whether this checkpoint has at least one active scan method configured.
     * Called by CheckpointScanService to give a useful error when a checkpoint
     * exists in the DB but has no method set.
     */
    public boolean hasScanMethod() {
        return qrCode != null || nfcTagUid != null || bleBeaconId != null;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
