package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A pay run that is part of an EMP201. A run belongs to at most one return. */
@Entity
@Table(name = "hr_emp201_runs")
@Getter
@NoArgsConstructor
public class HrEmp201Run {

    @Id @Column(name = "pay_run_id") UUID payRunId;
    @Column(name = "emp201_id") UUID emp201Id;

    public static HrEmp201Run of(UUID emp201Id, UUID payRunId) {
        HrEmp201Run l = new HrEmp201Run();
        l.payRunId = payRunId;
        l.emp201Id = emp201Id;
        return l;
    }
}
