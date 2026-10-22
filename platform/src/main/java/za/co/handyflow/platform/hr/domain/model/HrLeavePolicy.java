package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A company's limit on annual leave carried into a new year. No row, or no cap, means no limit. */
@Entity
@Table(name = "hr_leave_policy")
@Getter
@NoArgsConstructor
public class HrLeavePolicy {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "carry_over_cap_days") BigDecimal carryOverCapDays;
    @Column(name = "effective_year")      Integer effectiveYear;
    @Column(name = "agreement_ref")       String agreementRef;
    @Column(name = "updated_by")          UUID updatedBy;
    @Column(name = "updated_at")          Instant updatedAt;

    public static HrLeavePolicy none(UUID tenantId) {
        HrLeavePolicy p = new HrLeavePolicy();
        p.tenantId = tenantId;
        p.updatedAt = Instant.now();
        return p;
    }

    public void set(BigDecimal cap, Integer effectiveYear, String agreementRef, UUID by) {
        this.carryOverCapDays = cap;
        this.effectiveYear = cap == null ? null : effectiveYear;
        this.agreementRef = cap == null ? null : (agreementRef == null ? null : agreementRef.trim());
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
