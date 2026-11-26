package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An amount an employee gets (or has taken) every pay period of their group. */
@Entity
@Table(name = "pay_employee_components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayEmployeeComponent {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_employee_id", nullable = false)
    private UUID payEmployeeId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public static PayEmployeeComponent create(UUID tenantId, UUID payEmployeeId, UUID componentId, BigDecimal amount) {
        PayEmployeeComponent e = new PayEmployeeComponent();
        e.tenantId = tenantId;
        e.payEmployeeId = payEmployeeId;
        e.componentId = componentId;
        e.amount = amount;
        return e;
    }

    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
