package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A reading someone says is wrong. While it is OPEN, no kilometre charge can be raised on that reading and pending charges
 * built on it are held back from invoicing. It ends CONFIRMED (the reading stands) or CORRECTED (a correction replaced it).
 */
@Entity
@Table(name = "earthmoving_meter_disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthMeterDispute {

    public static final String OPEN = "OPEN";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CORRECTED = "CORRECTED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "reading_id", nullable = false)
    private UUID readingId;

    @Column(nullable = false)
    private String status = OPEN;

    @Column(nullable = false)
    private String reason;

    @Column(name = "raised_by")
    private UUID raisedBy;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static EarthMeterDispute raise(TenantId tenantId, UUID assetId, UUID readingId, String reason, UUID actor) {
        EarthMeterDispute d = new EarthMeterDispute();
        d.tenantId = tenantId;
        d.assetId = assetId;
        d.readingId = readingId;
        d.reason = MeterRules.checkDispute(reason);
        d.raisedBy = actor;
        d.raisedAt = Instant.now();
        return d;
    }

    public boolean isOpen() { return OPEN.equals(status); }

    public void confirm(String note, UUID actor) {
        if (!isOpen()) throw new IllegalStateException("This dispute is already closed.");
        this.resolutionNote = MeterRules.checkResolution(note);
        close(CONFIRMED, actor);
    }

    /** The reading was replaced by a correction, which carries its own reason. */
    public void corrected(String note, UUID actor) {
        if (!isOpen()) throw new IllegalStateException("This dispute is already closed.");
        this.resolutionNote = note;
        close(CORRECTED, actor);
    }

    private void close(String to, UUID actor) {
        this.status = to;
        this.resolvedBy = actor;
        this.resolvedAt = Instant.now();
    }
}
