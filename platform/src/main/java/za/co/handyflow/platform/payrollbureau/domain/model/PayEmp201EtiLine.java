package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One employee's ETI for a tax month, with the reasons when none was due. */
@Entity
@Table(name = "pay_emp201_eti_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayEmp201EtiLine {

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "emp201_return_id", nullable = false) private UUID emp201ReturnId;
    @Column(name = "pay_employee_id", nullable = false) private UUID payEmployeeId;
    @Column(nullable = false) private boolean eligible;
    private String tier;
    @Column(nullable = false) private BigDecimal remuneration;
    @Column(nullable = false) private BigDecimal incentive;
    private String reasons;
    @Column(name = "rule_version", nullable = false) private String ruleVersion;

    public static PayEmp201EtiLine create(UUID tenantId, UUID returnId, UUID employeeId, boolean eligible, String tier,
                                          BigDecimal remuneration, BigDecimal incentive, String reasons, String ruleVersion) {
        PayEmp201EtiLine l = new PayEmp201EtiLine();
        l.tenantId = tenantId; l.emp201ReturnId = returnId; l.payEmployeeId = employeeId; l.eligible = eligible;
        l.tier = tier; l.remuneration = remuneration; l.incentive = incentive; l.reasons = reasons; l.ruleVersion = ruleVersion;
        return l;
    }
}
