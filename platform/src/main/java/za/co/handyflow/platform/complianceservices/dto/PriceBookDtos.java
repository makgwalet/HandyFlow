package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PriceBookDtos {

    private PriceBookDtos() {}

    public record LineInput(@NotBlank @Size(max = 60) String serviceCode, @NotBlank @Size(max = 20) String pricingModel, BigDecimal baseFee,
                            BigDecimal unitRate, @Size(max = 30) String hourlyGrade, BigDecimal minFee, BigDecimal urgentSurchargePct,
                            @Size(max = 12) String taxTreatment, @Size(max = 500) String notes) {}

    public record RateInput(@NotBlank @Size(max = 30) String grade, BigDecimal hourlyRate, BigDecimal costRate) {}

    public record SaveRequest(@Size(max = 120) String label, @Size(max = 2000) String changeSummary, @Valid List<LineInput> lines, @Valid List<RateInput> rates) {}

    public record ApproveRequest(LocalDate effectiveFrom, @Size(max = 1000) String exceptionReason, @Size(max = 2000) String note) {}

    public record RejectRequest(@Size(max = 2000) String reason) {}

    public record RetireRequest(LocalDate on) {}

    public record LineView(String serviceCode, String serviceName, String pricingModel, BigDecimal baseFee, BigDecimal unitRate, String hourlyGrade,
                           BigDecimal minFee, BigDecimal urgentSurchargePct, String taxTreatment, String notes, List<String> gaps) {}

    /** costRate is null for anyone who is not an administrator. */
    public record RateView(String grade, BigDecimal hourlyRate, BigDecimal costRate) {}

    public record VersionView(UUID id, int versionNumber, String reviewStatus, String currency, String label, String changeSummary, UUID authoredBy,
                              UUID submittedBy, Instant submittedAt, UUID reviewedBy, Instant reviewedAt, String reviewNote, String reviewExceptionReason,
                              LocalDate effectiveFrom, LocalDate effectiveTo, boolean inForce, List<LineView> lines, List<RateView> rates) {}

    public record ExtraInput(@NotBlank @Size(max = 200) String description, BigDecimal quantity, BigDecimal unitPrice) {}

    public record DisbursementInput(@NotBlank @Size(max = 200) String description, BigDecimal amount, boolean vatable) {}

    public record CalculateRequest(@NotBlank @Size(max = 60) String serviceCode, LocalDate asOf, BigDecimal units, BigDecimal hours, BigDecimal complexityPct,
                                   boolean urgent, @Valid List<ExtraInput> extras, @Valid List<DisbursementInput> disbursements, BigDecimal discountPct) {}

    public record PartView(String label, BigDecimal amount) {}

    public record CalculateResponse(String serviceCode, int priceBookVersion, String currency, List<PartView> parts, BigDecimal professionalBeforeDiscount,
                                    BigDecimal discount, BigDecimal professional, BigDecimal disbursements, BigDecimal vat, BigDecimal vatRatePct,
                                    BigDecimal total, String discountApprover) {}

    public record MarginRequest(@NotBlank @Size(max = 30) String grade, BigDecimal hours, BigDecimal externalCosts, BigDecimal overheadPct,
                                BigDecimal targetMarginPct, BigDecimal roundStep, BigDecimal proposedPrice, LocalDate asOf) {}

    public record MarginResponse(int priceBookVersion, BigDecimal cost, BigDecimal recommendedPrice, BigDecimal roundedPrice, BigDecimal marginAtRounded,
                                 BigDecimal marginAtProposed) {}
}
