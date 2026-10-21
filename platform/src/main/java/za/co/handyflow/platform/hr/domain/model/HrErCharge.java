package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One charge on a notice of hearing, and the finding on it. */
@Entity
@Table(name = "hr_er_charges")
@Getter
@NoArgsConstructor
public class HrErCharge {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "case_id")   UUID caseId;
    int seq;
    String charge;
    String finding;
    @Column(name = "finding_note") String findingNote;

    public static HrErCharge of(UUID tenantId, UUID caseId, int seq, String charge) {
        HrErCharge c = new HrErCharge();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.caseId = caseId;
        c.seq = seq;
        c.charge = charge;
        return c;
    }

    public void setText(String charge) { this.charge = charge; }

    public void find(String finding, String note) {
        this.finding = finding;
        this.findingNote = note;
    }
}
