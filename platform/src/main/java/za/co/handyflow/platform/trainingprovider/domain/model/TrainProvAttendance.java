package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One learner's attendance at one slot. Corrected with a reason, never deleted. */
@Entity(name = "TrainProvAttendance")
@Table(name = "trainprov_attendance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAttendance {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "slot_id", nullable = false)
    private UUID slotId;
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
    @Column(nullable = false)
    private String status;
    private String note;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAttendance create(TenantId t, UUID slotId, UUID memberId, String status, String note, UUID by) {
        TrainProvAttendance a = new TrainProvAttendance();
        a.tenantId = t.getValue();
        a.slotId = slotId;
        a.memberId = memberId;
        a.status = status;
        a.note = note == null || note.isBlank() ? null : note.trim();
        a.recordedBy = by;
        a.recordedAt = Instant.now();
        a.updatedAt = a.recordedAt;
        return a;
    }

    public void correct(String status, String note) {
        this.status = status;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.updatedAt = Instant.now();
    }
}
