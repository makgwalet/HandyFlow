package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayPricingDtos {
    private PayPricingDtos() {}

    public record PricingResponse(BigDecimal perEmployeeFee, BigDecimal minimumFee, List<ChargeResponse> charges) {}

    public record ChargeResponse(UUID id, String description, BigDecimal amount, String billing) {}

    public record UpdatePricingRequest(@NotNull BigDecimal perEmployeeFee, @NotNull BigDecimal minimumFee) {}

    public record CreateChargeRequest(@NotBlank String description, @NotNull BigDecimal amount, @NotBlank String billing) {}

    public record CancelFeeNoteRequest(@NotBlank String reason) {}

    public record UpdateCostRequest(@NotNull BigDecimal costPerPayslip) {}

    public record CostResponse(BigDecimal costPerPayslip) {}

    /** One client's line in the profitability report. Revenue is by invoice date, cost by pay date. */
    public record ProfitabilityClient(UUID clientId, String clientName, int runs, int payslips,
                                      BigDecimal revenue, BigDecimal cost, BigDecimal margin, BigDecimal marginPercent,
                                      int unbilledRuns, BigDecimal unbilledEstimate) {}

    public record ProfitabilityResponse(LocalDate from, LocalDate to, BigDecimal costPerPayslip,
                                        BigDecimal totalRevenue, BigDecimal totalCost, BigDecimal totalMargin,
                                        BigDecimal totalMarginPercent, int totalUnbilledRuns, BigDecimal totalUnbilledEstimate,
                                        List<ProfitabilityClient> clients) {}
}
