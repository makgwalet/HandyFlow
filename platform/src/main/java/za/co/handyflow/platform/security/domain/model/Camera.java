// security/domain/model/Camera.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Camera — a registered CCTV camera at a site.
 *
 * Stores connection metadata only — provider type and a vendor-specific
 * config blob (RTSP URL, ONVIF endpoint, or cloud API credentials). No video
 * streaming or storage happens in this platform; per the original audit
 * (Part 2.4), the job here is the software layer that unifies event
 * ingestion across vendors, not building video infrastructure.
 *
 * Motion/alarm events from this camera flow into the existing AlarmEvent
 * pipeline (Phase 3 Control Room) with source=CCTV_MOTION and cameraId set —
 * this entity doesn't have its own event log, it's purely the registry +
 * webhook verification secret.
 *
 * lastEventAt is a liveness signal — updated every time a motion event is
 * ingested from this camera, so a supervisor can spot a camera that's gone
 * quiet (offline, disconnected, vendor outage) without checking each one
 * manually.
 *
 * WHY connectionConfig is a raw JSON string, not a typed object?
 * See V116 migration header — different vendors need genuinely different
 * config shapes, and JSONB on the DB side means the column doesn't need a
 * migration every time a new provider is added. The application layer
 * validates the expected shape per provider, not the entity.
 */
@Entity
@Table(name = "security_cameras")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Camera {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CameraProvider provider = CameraProvider.NONE;

    // NOT YET ENCRYPTED — may contain credentials/API keys. See V116 header.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "connection_config", columnDefinition = "jsonb")
    private String connectionConfig;

    @Column(name = "webhook_secret", length = 100)
    private String webhookSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CameraStatus status = CameraStatus.ACTIVE;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static Camera register(TenantId tenantId, UUID siteId, String name,
                                  CameraProvider provider, String connectionConfig,
                                  String webhookSecret, String notes) {
        Camera c           = new Camera();
        c.tenantId         = tenantId;
        c.siteId           = siteId;
        c.name             = name.strip();
        c.provider         = provider != null ? provider : CameraProvider.NONE;
        c.connectionConfig = connectionConfig;
        c.webhookSecret    = webhookSecret;
        c.status           = CameraStatus.ACTIVE;
        c.notes            = notes;
        c.createdAt        = Instant.now();
        c.updatedAt        = Instant.now();
        return c;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void update(String name, CameraProvider provider, String connectionConfig,
                       String notes) {
        this.name             = name.strip();
        this.provider         = provider;
        this.connectionConfig = connectionConfig;
        this.notes            = notes;
        this.updatedAt        = Instant.now();
    }

    public void rotateWebhookSecret(String newSecret) {
        this.webhookSecret = newSecret;
        this.updatedAt     = Instant.now();
    }

    /** Called on every successfully ingested motion event from this camera. */
    public void recordEvent() {
        this.lastEventAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void markOffline() {
        this.status    = CameraStatus.OFFLINE;
        this.updatedAt = Instant.now();
    }

    public void markActive() {
        if (this.status == CameraStatus.DECOMMISSIONED) {
            throw new IllegalStateException("Cannot reactivate a decommissioned camera");
        }
        this.status    = CameraStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void decommission() {
        this.status    = CameraStatus.DECOMMISSIONED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return status == CameraStatus.ACTIVE; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum CameraProvider {
        HIKVISION_CLOUD, DAHUA_CLOUD, ONVIF, RTSP_GENERIC, OTHER, NONE
    }

    public enum CameraStatus {
        ACTIVE, OFFLINE, DECOMMISSIONED
    }
}
