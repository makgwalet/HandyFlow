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

/** A company's limit on annual leave carried into a new year, and how long carried leave lasts. No row, or no cap, means no limit. */
@Entity
@Table(name = "hr_leave_policy")
@Getter
@NoArgsConstructor
public class HrLeavePolicy {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "carry_over_cap_days") BigDecimal carryOverCapDays;
    @Column(name = "carry_expiry_months") Integer carryExpiryMonths;
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

    public void set(BigDecimal cap, Integer effectiveYear, String agreementRef, UUID by) { set(cap, null, effectiveYear, agreementRef, by); }

    public void set(BigDecimal cap, Integer expiryMonths, Integer effectiveYear, String agreementRef, UUID by) {
        boolean none = cap == null && expiryMonths == null;
        this.carryOverCapDays = cap;
        this.carryExpiryMonths = expiryMonths;
        this.effectiveYear = none ? null : effectiveYear;
        this.agreementRef = none ? null : (agreementRef == null ? null : agreementRef.trim());
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
