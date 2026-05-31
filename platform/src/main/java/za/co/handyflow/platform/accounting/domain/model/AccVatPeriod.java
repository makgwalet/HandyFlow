package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "acc_vat_periods")
@Getter
@NoArgsConstructor
public class AccVatPeriod {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    @Column(name = "period_start") LocalDate periodStart;
    @Column(name = "period_end")   LocalDate periodEnd;
    String status;
    @Column(name = "output_vat")   BigDecimal outputVat  = BigDecimal.ZERO;
    @Column(name = "input_vat")    BigDecimal inputVat   = BigDecimal.ZERO;
    @Column(name = "submitted_at") Instant submittedAt;
    @Column(name = "created_at")   Instant createdAt;
    @Column(name = "updated_at")   Instant updatedAt;

    public static AccVatPeriod create(TenantId tenantId,
                                      LocalDate periodStart, LocalDate periodEnd) {
        AccVatPeriod v = new AccVatPeriod();
        v.id          = UUID.randomUUID();
        v.tenantId    = tenantId.getValue();
        v.periodStart = periodStart;
        v.periodEnd   = periodEnd;
        v.status      = "OPEN";
        v.outputVat   = BigDecimal.ZERO;
        v.inputVat    = BigDecimal.ZERO;
        v.createdAt   = Instant.now();
        v.updatedAt   = Instant.now();
        return v;
    }

    public void addOutputVat(BigDecimal amount) {
        this.outputVat = this.outputVat.add(amount);
        this.updatedAt = Instant.now();
    }

    public void addInputVat(BigDecimal amount) {
        this.inputVat  = this.inputVat.add(amount);
        this.updatedAt = Instant.now();
    }

    public BigDecimal getVatPayable() {
        return outputVat.subtract(inputVat);
    }

    public void close()  { this.status = "CLOSED"; this.updatedAt = Instant.now(); }
    public void submit() {
        this.status      = "SUBMITTED";
        this.submittedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }
}