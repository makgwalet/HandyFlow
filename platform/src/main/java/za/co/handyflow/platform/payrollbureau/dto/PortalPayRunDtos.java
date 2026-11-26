package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a client's portal user sees of a pay run: no bureau user ids and no bank details. */
public final class PortalPayRunDtos {

    private PortalPayRunDtos() {}

    public record PortalPayRun(UUID id, String payRunNumber, LocalDate periodStart, LocalDate periodEnd, LocalDate payDate,
                               String status, BigDecimal totalGross, BigDecimal totalPaye, BigDecimal totalUif, BigDecimal totalSdl,
                               BigDecimal totalNet, Integer employeeCount, Instant sentToClientAt, Instant clientApprovedAt,
                               Instant clientDeclinedAt, String clientDeclineReason) {}

    public record PortalPayRunDetail(PortalPayRun run, List<PayslipResponse> payslips, PayRunReviewResponse review) {}

    public record DeclineRequest(String reason) {}
}
