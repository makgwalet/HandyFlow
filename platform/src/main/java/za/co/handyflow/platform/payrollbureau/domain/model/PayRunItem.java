package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An amount for one employee in one pay run only (overtime this week, a bonus, a one-off deduction). */
@Entity
@Table(name = "pay_run_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayRunItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_run_id", nullable = false)
    private UUID payRunId;

    @Column(name = "pay_employee_id", nullable = false)
    private UUID payEmployeeId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public static PayRunItem create(UUID tenantId, UUID payRunId, UUID payEmployeeId, UUID componentId, BigDecimal amount, String note) {
        PayRunItem i = new PayRunItem();
        i.tenantId = tenantId;
        i.payRunId = payRunId;
        i.payEmployeeId = payEmployeeId;
        i.componentId = componentId;
        i.amount = amount;
        i.note = note;
        return i;
    }

    public void change(BigDecimal amount, String note) {
        this.amount = amount;
        this.note = note;
    }
}
