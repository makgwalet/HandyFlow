package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A check-in on an improvement plan. Kept as written; the database refuses to change or delete them. */
@Entity
@Table(name = "hr_perf_checkins")
@Getter
@NoArgsConstructor
public class HrPerfCheckin {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "plan_id") UUID planId;
    @Column(name = "on_date") LocalDate onDate;
    String progress;
    String note;
    @Column(name = "done_by") UUID doneBy;
    @Column(name = "done_at") Instant doneAt;

    public static HrPerfCheckin of(UUID tenantId, UUID planId, LocalDate on, String progress, String note, UUID by) {
        HrPerfCheckin c = new HrPerfCheckin();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.planId = planId;
        c.onDate = on;
        c.progress = progress;
        c.note = note.trim();
        c.doneBy = by;
        c.doneAt = Instant.now();
        return c;
    }
}
