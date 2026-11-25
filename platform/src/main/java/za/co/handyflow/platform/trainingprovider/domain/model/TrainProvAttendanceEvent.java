package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an attendance entry's history. Written once, never changed. */
@Entity(name = "TrainProvAttendanceEvent")
@Table(name = "trainprov_attendance_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAttendanceEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "attendance_id", nullable = false, updatable = false)
    private UUID attendanceId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(name = "from_status", updatable = false)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;
    @Column(updatable = false)
    private String reason;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvAttendanceEvent of(TenantId t, UUID attendanceId, String action, String from, String to, String reason, UUID by) {
        TrainProvAttendanceEvent e = new TrainProvAttendanceEvent();
        e.tenantId = t.getValue();
        e.attendanceId = attendanceId;
        e.action = action;
        e.fromStatus = from;
        e.toStatus = to;
        e.reason = reason == null || reason.isBlank() ? null : reason.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}
