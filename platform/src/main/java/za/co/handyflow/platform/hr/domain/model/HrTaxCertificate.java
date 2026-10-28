package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An issued IRP5 or IT3(a). The database refuses changes other than cancelling and releasing it. */
@Entity
@Table(name = "hr_tax_certificates")
@Getter
@NoArgsConstructor
public class HrTaxCertificate {

    @Id UUID id;
    @Column(name = "tenant_id")          UUID tenantId;
    @Column(name = "tax_year")           int taxYear;
    String period;
    @Column(name = "employee_id")        UUID employeeId;
    @Column(name = "certificate_number") String certificateNumber;
    @Column(name = "certificate_type")   String certificateType;
    int version = 1;
    String status = "ISSUED";
    @Column(name = "employee_number")    String employeeNumber;
    @Column(name = "first_names")        String firstNames;
    String surname;
    @Column(name = "id_number")          String idNumber;
    @Column(name = "tax_number")         String taxNumber;
    @Column(name = "passport_number")    String passportNumber;
    @Column(name = "passport_country")   String passportCountry;
    @Column(name = "date_of_birth")      LocalDate dateOfBirth;
    @Column(name = "employer_paye_ref")  String employerPayeRef;
    @Column(name = "period_from")        LocalDate periodFrom;
    @Column(name = "period_to")          LocalDate periodTo;
    @Column(name = "payslip_count")      int payslipCount;
    String reason;
    @Column(name = "supersedes_id")      UUID supersedesId;
    @Column(name = "issued_by")          UUID issuedBy;
    @Column(name = "issued_at")          Instant issuedAt;
    @Column(name = "cancelled_by")       UUID cancelledBy;
    @Column(name = "cancelled_at")       Instant cancelledAt;
    @Column(name = "cancel_reason")      String cancelReason;
    @Column(name = "released_by")        UUID releasedBy;
    @Column(name = "released_at")        Instant releasedAt;

    public static HrTaxCertificate issue(UUID tenantId, int taxYear, String period, HrEmployee emp, String number, String type,
                                         int version, String employerPayeRef, LocalDate from, LocalDate to, int payslips,
                                         String reason, UUID supersedes, UUID actor) {
        HrTaxCertificate c = new HrTaxCertificate();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.taxYear = taxYear;
        c.period = period;
        c.employeeId = emp.getId();
        c.certificateNumber = number;
        c.certificateType = type;
        c.version = version;
        c.employeeNumber = emp.getEmployeeNumber();
        c.firstNames = emp.getFirstName();
        c.surname = emp.getLastName();
        c.idNumber = emp.getIdNumber();
        c.taxNumber = emp.getTaxNumber();
        c.passportNumber = emp.getPassportNumber();
        c.passportCountry = emp.getPassportCountry();
        c.dateOfBirth = emp.getDateOfBirth();
        c.employerPayeRef = employerPayeRef;
        c.periodFrom = from;
        c.periodTo = to;
        c.payslipCount = payslips;
        c.reason = reason;
        c.supersedesId = supersedes;
        c.issuedBy = actor;
        c.issuedAt = Instant.now();
        return c;
    }

    public void cancel(UUID actor, String why) {
        this.status = "CANCELLED";
        this.cancelledBy = actor;
        this.cancelledAt = Instant.now();
        this.cancelReason = why;
    }

    public void release(UUID actor) {
        this.releasedBy = actor;
        this.releasedAt = Instant.now();
    }
}
