package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What happened on a case, by whom and when. The database refuses to change or delete these. */
@Entity
@Table(name = "hr_er_events")
@Getter
@NoArgsConstructor
public class HrErEvent {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "case_id")   UUID caseId;
    String action;
    String note;
    @Column(name = "visible_to_employee") boolean visibleToEmployee;
    @Column(name = "done_by") UUID doneBy;
    @Column(name = "done_at") Instant doneAt;

    public static HrErEvent of(UUID tenantId, UUID caseId, String action, String note, boolean visibleToEmployee, UUID by) {
        HrErEvent e = new HrErEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.caseId = caseId;
        e.action = action;
        e.note = note != null && note.length() > 2000 ? note.substring(0, 2000) : note;
        e.visibleToEmployee = visibleToEmployee;
        e.doneBy = by;
        e.doneAt = Instant.now();
        return e;
    }
}
