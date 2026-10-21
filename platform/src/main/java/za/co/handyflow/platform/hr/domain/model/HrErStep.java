package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/** One step an incapacity process must show was taken before a dismissal. Created with the case, ticked as they are done. */
@Entity
@Table(name = "hr_er_steps")
@Getter
@NoArgsConstructor
public class HrErStep {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "case_id")   UUID caseId;
    String code;
    String label;
    int seq;
    @Column(name = "done_on") LocalDate doneOn;
    @Column(name = "done_by") UUID doneBy;
    String note;

    public static HrErStep of(UUID tenantId, UUID caseId, int seq, IncapacityRules.Step s) {
        HrErStep t = new HrErStep();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.caseId = caseId;
        t.seq = seq;
        t.code = s.code();
        t.label = s.label();
        return t;
    }

    public boolean isDone() { return doneOn != null; }

    public void done(LocalDate on, UUID by, String note) { this.doneOn = on; this.doneBy = by; this.note = note; }
    public void undo() { this.doneOn = null; this.doneBy = null; this.note = null; }
}
