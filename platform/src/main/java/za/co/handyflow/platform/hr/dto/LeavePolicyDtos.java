package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LeavePolicyDtos {

    private LeavePolicyDtos() {}

    /** A null cap and a null expiry remove the policy. {@code carryExpiryMonths}: carried leave unused after this many months goes. */
    public record PolicyRequest(BigDecimal carryOverCapDays, Integer carryExpiryMonths, Integer effectiveYear, String agreementRef) {
        public PolicyRequest(BigDecimal carryOverCapDays, Integer effectiveYear, String agreementRef) { this(carryOverCapDays, null, effectiveYear, agreementRef); }
    }

    public record PolicyView(BigDecimal carryOverCapDays, Integer carryExpiryMonths, Integer effectiveYear, String agreementRef, Instant updatedAt) {
        public PolicyView(BigDecimal carryOverCapDays, Integer effectiveYear, String agreementRef, Instant updatedAt) { this(carryOverCapDays, null, effectiveYear, agreementRef, updatedAt); }
    }

    /** What one employee would lose at the coming rollover if no more leave were taken. */
    public record AtRisk(UUID employeeId, String employeeName, BigDecimal availableNow, BigDecimal projectedClosing, BigDecimal wouldLose) {}

    /** Carried-over days an employee has not yet used, which go on {@code expiresOn} if still unused. */
    public record Expiring(UUID employeeId, String employeeName, BigDecimal unused, java.time.LocalDate expiresOn) {}

    public record AtRiskReport(BigDecimal carryOverCapDays, Integer rolloverInto, List<AtRisk> employees, Integer carryExpiryMonths, java.time.LocalDate expiringOn, List<Expiring> expiring) {
        public AtRiskReport(BigDecimal carryOverCapDays, Integer rolloverInto, List<AtRisk> employees) { this(carryOverCapDays, rolloverInto, employees, null, null, List.of()); }
    }
}
