// security/domain/model/AlarmEvent.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * AlarmEvent — a raw event from any control-room source: alarm panel webhook,
 * panic button press, CCTV motion detection, drone observation, or a guard's
 * duress trigger.
 *
 * This is the ingestion point for everything that flows into the control room
 * triage queue. One generic table with a `source` discriminator rather than
 * separate tables per source — Part 9's duress events and Part 7's panic
 * button hardware both feed this same pipeline (source=DURESS, source=PANIC_BUTTON)
 * without requiring a parallel triage/dispatch system to be built for each.
 *
 * Lifecycle:
 *   NEW         → event just landed, nobody has looked at it
 *   TRIAGED     → control room operator has reviewed and assessed severity
 *   DISPATCHED  → a response unit has been sent (see Dispatch)
 *   RESOLVED    → the situation is closed out
 *   FALSE_ALARM → triaged and determined to require no response
 *
 * WHY store rawPayload as a String (mapped to JSONB) rather than parsing
 * into typed fields?
 * Different alarm panel vendors send wildly different payload shapes. Storing
 * the raw body verbatim means nothing is lost even if our parser doesn't
 * understand every field a given panel sends — it's reference material for
 * dispute resolution, not a structured data source the app relies on.
 */
@Entity
@Table(name = "security_alarm_events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AlarmEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSource source;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmSeverity severity = AlarmSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlarmStatus status = AlarmStatus.NEW;

    @Column(name = "triggered_by_guard_id")
    private UUID triggeredByGuardId;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column
    private String description;

    @Column(name = "triaged_by")
    private UUID triagedBy;

    @Column(name = "triaged_at")
    private Instant triagedAt;

    @Column(name = "linked_incident_id")
    private UUID linkedIncidentId;

    @Column(name = "camera_id")
    private UUID cameraId;

    @Column(name = "protection_detail_id")
    private UUID protectionDetailId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static AlarmEvent ingest(TenantId tenantId, UUID siteId, AlarmSource source,
                                    String rawPayload, AlarmSeverity severity,
                                    UUID triggeredByGuardId, BigDecimal latitude,
                                    BigDecimal longitude, String description) {
        AlarmEvent e            = new AlarmEvent();
        e.tenantId               = tenantId;
        e.siteId                 = siteId;
        e.source                 = source;
        e.rawPayload             = rawPayload;
        e.severity                = severity != null ? severity : AlarmSeverity.MEDIUM;
        e.status                  = AlarmStatus.NEW;
        e.triggeredByGuardId      = triggeredByGuardId;
        e.latitude                = latitude;
        e.longitude               = longitude;
        e.description             = description;
        e.createdAt                = Instant.now();
        e.updatedAt                = Instant.now();
        return e;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void triage(UUID operatorId, AlarmSeverity severity) {
        if (this.status != AlarmStatus.NEW) {
            throw new IllegalStateException("Can only triage a NEW event (current: " + status + ")");
        }
        this.status     = AlarmStatus.TRIAGED;
        this.triagedBy  = operatorId;
        this.triagedAt  = Instant.now();
        if (severity != null) this.severity = severity;
        this.updatedAt  = Instant.now();
    }

    /** Called by DispatchService when a Dispatch row is created for this event. */
    public void markDispatched() {
        if (this.status != AlarmStatus.TRIAGED && this.status != AlarmStatus.NEW) {
            throw new IllegalStateException(
                    "Cannot dispatch an event in status " + this.status);
        }
        this.status    = AlarmStatus.DISPATCHED;
        this.updatedAt = Instant.now();
    }

    public void resolve(UUID linkedIncidentId) {
        this.status            = AlarmStatus.RESOLVED;
        this.linkedIncidentId  = linkedIncidentId;
        this.updatedAt         = Instant.now();
    }

    public void markFalseAlarm() {
        this.status    = AlarmStatus.FALSE_ALARM;
        this.updatedAt = Instant.now();
    }

    /** Called when this event originated from a registered camera (source=CCTV_MOTION). */
    public void linkCamera(UUID cameraId) {
        this.cameraId  = cameraId;
        this.updatedAt = Instant.now();
    }

    /** Called when this event is a duress trigger (source=DURESS) on a CP detail. */
    public void linkProtectionDetail(UUID protectionDetailId) {
        this.protectionDetailId = protectionDetailId;
        this.updatedAt          = Instant.now();
    }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum AlarmSource {
        ALARM_PANEL, PANIC_BUTTON, CCTV_MOTION, DRONE, DURESS, MANUAL, OTHER
    }

    public enum AlarmSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlarmStatus {
        NEW, TRIAGED, DISPATCHED, RESOLVED, FALSE_ALARM
    }
}
