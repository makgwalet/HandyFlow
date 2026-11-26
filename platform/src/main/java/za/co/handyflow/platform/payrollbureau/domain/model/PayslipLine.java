package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One component line on a payslip, copied when the pay run is processed so later catalogue edits cannot change it. */
@Entity
@Table(name = "pay_payslip_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayslipLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "payslip_id", nullable = false)
    private UUID payslipId;

    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "sars_code")
    private String sarsCode;

    @Column(name = "source", nullable = false)
    private String source; // RECURRING | RUN

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static PayslipLine of(UUID tenantId, UUID payslipId, PayComponent c, String source, BigDecimal amount, String note) {
        PayslipLine l = new PayslipLine();
        l.tenantId = tenantId;
        l.payslipId = payslipId;
        l.componentId = c.getId();
        l.code = c.getCode();
        l.name = c.getName();
        l.role = c.getRole();
        l.sarsCode = c.getSarsCode();
        l.source = source;
        l.amount = amount;
        l.note = note;
        return l;
    }
}
