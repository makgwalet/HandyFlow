package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What happened when this payslip was emailed (audit D10). */
@Entity
@Table(name = "pay_payslip_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayslipDelivery {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pay_run_id", nullable = false) private UUID payRunId;
    @Column(name = "payslip_id", nullable = false) private UUID payslipId;
    @Column(name = "pay_employee_id", nullable = false) private UUID payEmployeeId;
    @Column(name = "status", nullable = false) private String status; // SENT | FAILED | NO_EMAIL
    @Column(name = "email_to") private String emailTo;
    @Column(name = "attempts", nullable = false) private int attempts;
    @Column(name = "last_error") private String lastError;
    @Column(name = "last_attempt_at", nullable = false) private Instant lastAttemptAt = Instant.now();
    @Column(name = "sent_at") private Instant sentAt;

    @Version
    private Long version;

    public static PayslipDelivery forPayslip(UUID tenantId, Payslip p) {
        PayslipDelivery d = new PayslipDelivery();
        d.tenantId = tenantId;
        d.payRunId = p.getPayRunId();
        d.payslipId = p.getId();
        d.payEmployeeId = p.getPayEmployeeId();
        d.status = "NO_EMAIL";
        return d;
    }

    public void sent(String to) {
        this.attempts++; this.status = "SENT"; this.emailTo = to; this.lastError = null;
        this.lastAttemptAt = Instant.now(); this.sentAt = this.lastAttemptAt;
    }

    public void failed(String to, String error) {
        this.attempts++; this.status = "FAILED"; this.emailTo = to;
        this.lastError = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 500));
        this.lastAttemptAt = Instant.now();
    }

    public void noEmail() {
        this.status = "NO_EMAIL"; this.emailTo = null; this.lastAttemptAt = Instant.now();
    }
}
