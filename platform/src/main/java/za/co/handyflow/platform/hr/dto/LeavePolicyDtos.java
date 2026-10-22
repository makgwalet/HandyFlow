package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LeavePolicyDtos {

    private LeavePolicyDtos() {}

    /** A null cap removes the limit. */
    public record PolicyRequest(BigDecimal carryOverCapDays, Integer effectiveYear, String agreementRef) {}

    public record PolicyView(BigDecimal carryOverCapDays, Integer effectiveYear, String agreementRef, Instant updatedAt) {}

    /** What one employee would lose at the coming rollover if no more leave were taken. */
    public record AtRisk(UUID employeeId, String employeeName, BigDecimal availableNow, BigDecimal projectedClosing, BigDecimal wouldLose) {}

    public record AtRiskReport(BigDecimal carryOverCapDays, Integer rolloverInto, List<AtRisk> employees) {}
}
