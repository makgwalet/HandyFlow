package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What happened to a cycle, review, goal or plan, by whom and when. The database refuses to change or delete these. */
@Entity
@Table(name = "hr_perf_events")
@Getter
@NoArgsConstructor
public class HrPerfEvent {

    public static final String CYCLE = "CYCLE", REVIEW = "REVIEW", GOAL = "GOAL", PLAN = "PLAN";

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    String kind;
    @Column(name = "ref_id") UUID refId;
    String action;
    String note;
    @Column(name = "done_by") UUID doneBy;
    @Column(name = "done_at") Instant doneAt;

    public static HrPerfEvent of(UUID tenantId, String kind, UUID refId, String action, String note, UUID by) {
        HrPerfEvent e = new HrPerfEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.kind = kind;
        e.refId = refId;
        e.action = action;
        e.note = note;
        e.doneBy = by;
        e.doneAt = Instant.now();
        return e;
    }
}
