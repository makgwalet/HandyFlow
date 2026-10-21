package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What was changed on an attendance entry, by whom, and why. Cannot be edited or deleted. */
@Entity
@Table(name = "hr_attendance_log")
@Getter
@NoArgsConstructor
public class HrAttendanceLog {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "entry_id")  UUID entryId;
    String action;
    @Column(name = "old_in")    Instant oldIn;
    @Column(name = "old_out")   Instant oldOut;
    @Column(name = "new_in")    Instant newIn;
    @Column(name = "new_out")   Instant newOut;
    String reason;
    @Column(name = "done_by")    UUID doneBy;
    @Column(name = "done_at")    Instant doneAt;

    public static HrAttendanceLog of(UUID tenantId, UUID entryId, String action, Instant oldIn, Instant oldOut, Instant newIn, Instant newOut,
                                     String reason, UUID by) {
        HrAttendanceLog l = new HrAttendanceLog();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.entryId = entryId;
        l.action = action;
        l.oldIn = oldIn;
        l.oldOut = oldOut;
        l.newIn = newIn;
        l.newOut = newOut;
        l.reason = reason;
        l.doneBy = by;
        l.doneAt = Instant.now();
        return l;
    }
}
