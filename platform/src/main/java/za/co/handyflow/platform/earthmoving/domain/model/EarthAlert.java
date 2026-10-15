package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Something that needs a person's attention. Open until someone acknowledges it; resolved when the cause is dealt with. */
@Entity
@Table(name = "earthmoving_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthAlert {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false)
    private String severity;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private UUID refId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Version
    private Long version;

    public static EarthAlert create(TenantId tenantId, String alertType, String severity, UUID assetId, String refType, UUID refId,
                                    String title, String message, String dedupeKey) {
        EarthAlert a = new EarthAlert();
        a.tenantId = tenantId;
        a.alertType = alertType;
        a.severity = severity;
        a.assetId = assetId;
        a.refType = refType;
        a.refId = refId;
        a.title = title;
        a.message = message;
        a.dedupeKey = dedupeKey;
        a.createdAt = Instant.now();
        return a;
    }

    public boolean isOpen() { return !"RESOLVED".equals(status); }

    public void acknowledge(UUID by) {
        if ("RESOLVED".equals(status)) throw new IllegalStateException("This alert is already resolved.");
        if ("ACKNOWLEDGED".equals(status)) throw new IllegalStateException("This alert was already acknowledged.");
        this.status = "ACKNOWLEDGED";
        this.acknowledgedBy = by;
        this.acknowledgedAt = Instant.now();
    }

    public void resolve(UUID by, String note) {
        if ("RESOLVED".equals(status)) throw new IllegalStateException("This alert is already resolved.");
        this.status = "RESOLVED";
        this.resolvedBy = by;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }

    /** Only an alert nobody has acknowledged escalates, and only once. */
    public boolean canEscalate() { return "OPEN".equals(status) && level == 1; }

    public void escalate() {
        if (!canEscalate()) throw new IllegalStateException("This alert cannot be escalated.");
        this.level = 2;
        this.escalatedAt = Instant.now();
    }
}
