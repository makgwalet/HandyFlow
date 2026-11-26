package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The EMP201 figures for one client and tax month. A DRAFT can be prepared again; a FILED return is the record of what went to SARS. */
@Entity
@Table(name = "pay_emp201_returns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayEmp201Return {

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pay_client_id", nullable = false) private UUID payClientId;
    @Column(name = "tax_month", nullable = false) private LocalDate taxMonth;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "employee_count", nullable = false) private int employeeCount;
    @Column(name = "total_remuneration", nullable = false) private BigDecimal totalRemuneration;
    @Column(nullable = false) private BigDecimal paye;
    @Column(name = "uif_employee", nullable = false) private BigDecimal uifEmployee;
    @Column(name = "uif_employer", nullable = false) private BigDecimal uifEmployer;
    @Column(nullable = false) private BigDecimal sdl;
    @Column(name = "eti_calculated", nullable = false) private BigDecimal etiCalculated;
    @Column(name = "eti_brought_forward", nullable = false) private BigDecimal etiBroughtForward;
    @Column(name = "eti_used", nullable = false) private BigDecimal etiUsed;
    @Column(name = "eti_carried_forward", nullable = false) private BigDecimal etiCarriedForward;
    @Column(name = "eti_claimed", nullable = false) private boolean etiClaimed;
    @Column(name = "total_payable", nullable = false) private BigDecimal totalPayable;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "run_numbers") private String runNumbers;
    @Column(name = "prepared_by") private UUID preparedBy;
    @Column(name = "prepared_at", nullable = false) private Instant preparedAt = Instant.now();
    @Column(name = "filed_by") private UUID filedBy;
    @Column(name = "filed_at") private Instant filedAt;
    @Column(name = "filing_reference") private String filingReference;
    @Version private Long version;

    public static PayEmp201Return create(UUID tenantId, UUID clientId, LocalDate taxMonth) {
        PayEmp201Return r = new PayEmp201Return();
        r.tenantId = tenantId; r.payClientId = clientId; r.taxMonth = taxMonth;
        return r;
    }

    public boolean isFiled() { return "FILED".equals(status); }

    public void fill(int employees, BigDecimal remuneration, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer,
                     BigDecimal sdl, BigDecimal etiCalculated, BigDecimal etiBroughtForward, BigDecimal etiUsed,
                     BigDecimal etiCarriedForward, boolean etiClaimed, BigDecimal totalPayable, LocalDate dueDate,
                     String runNumbers, UUID preparedBy) {
        this.employeeCount = employees; this.totalRemuneration = remuneration; this.paye = paye;
        this.uifEmployee = uifEmployee; this.uifEmployer = uifEmployer; this.sdl = sdl;
        this.etiCalculated = etiCalculated; this.etiBroughtForward = etiBroughtForward; this.etiUsed = etiUsed;
        this.etiCarriedForward = etiCarriedForward; this.etiClaimed = etiClaimed; this.totalPayable = totalPayable;
        this.dueDate = dueDate; this.runNumbers = runNumbers; this.preparedBy = preparedBy; this.preparedAt = Instant.now();
    }

    public void markFiled(String reference, UUID by) {
        this.status = "FILED"; this.filingReference = reference; this.filedBy = by; this.filedAt = Instant.now();
    }
}
