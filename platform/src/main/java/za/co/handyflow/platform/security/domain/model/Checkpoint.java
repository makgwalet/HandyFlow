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
 * CHANGE (V217): added qrSecret + regenerateQr(). Previously QR signing
 * (CheckpointScanService.generateQrPayload/verifyQrHmac) used the parent
 * Site's shared qrSecret -- meaning invalidating one compromised
 * checkpoint's QR required rotating the secret for the ENTIRE site,
 * forcing every other checkpoint to be reprinted too. Each checkpoint now
 * has its own independent secret, same generation shape as
 * Site.qrSecret, so regeneration only ever affects the one checkpoint.
 *
 * Supports three verification methods:
 *   QR  — a unique UUID-based QR payload, printable and mountable on a wall.
 *   NFC — an NFC tag UID, typically a passive NTAG213/215 sticker.
 *   BLE — a Bluetooth Low Energy beacon identifier.
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
     * Legacy bare-UUID QR payload — matched directly (no signature) when a
     * checkpoint hasn't been reprinted since V217. New prints always use the
     * fully signed payload (see CheckpointScanService.generateQrPayload())
     * regardless of whether the site currently enforces signature
     * verification -- this column is purely a fallback for stickers that
     * predate the signed-QR feature.
     */
    @Column(name = "qr_code", nullable = false, unique = true)
    private String qrCode;

    /**
     * Per-checkpoint HMAC signing secret (V217). See class javadoc for why
     * this replaced the old site-wide secret.
     */
    @Column(name = "qr_secret", nullable = false, length = 64)
    private String qrSecret;

    @Column(name = "nfc_tag_uid")
    private String nfcTagUid;

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
        c.qrCode      = UUID.randomUUID().toString();
        c.qrSecret    = UUID.randomUUID().toString().replace("-", "");
        c.sortOrder   = sortOrder;
        c.active      = true;
        c.createdAt   = Instant.now();
        c.updatedAt   = Instant.now();
        return c;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public boolean hasScanMethod() {
        return qrCode != null || nfcTagUid != null || bleBeaconId != null;
    }

    /**
     * Rotates both the legacy bare-UUID code and the signing secret in one
     * action, so it correctly invalidates whichever physical sticker is
     * currently mounted -- an old pre-V217 bare-UUID print, or a
     * post-V217 signed print -- regardless of which one it happens to be.
     * The checkpoint must be reprinted (GET .../qr-payload or the QR PDF
     * endpoint) after calling this; scanning the old sticker will fail
     * immediately once this is saved.
     */
    public void regenerateQr() {
        this.qrCode    = UUID.randomUUID().toString();
        this.qrSecret  = UUID.randomUUID().toString().replace("-", "");
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}