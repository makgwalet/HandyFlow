package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.DisbursementInput;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.ExtraInput;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class QuoteDtos {

    private QuoteDtos() {}

    public record LineInput(@NotBlank @Size(max = 60) String serviceCode, @Size(max = 500) String description, BigDecimal units, BigDecimal hours,
                            BigDecimal complexityPct, boolean urgent, BigDecimal discountPct, @Valid List<ExtraInput> extras,
                            @Valid List<DisbursementInput> disbursements) {}

    public record SaveRequest(@Size(max = 255) String title, LocalDate validUntil, @Size(max = 4000) String notes, @Size(max = 4000) String terms,
                              @Valid List<LineInput> lines) {}

    public record ApproveRequest(@Size(max = 2000) String note, @Size(max = 1000) String exceptionReason) {}

    public record ReasonRequest(@Size(max = 2000) String reason) {}

    public record NoteRequest(@Size(max = 2000) String note) {}

    public record PartView(String label, BigDecimal amount) {}

    public record ExtraView(String description, BigDecimal quantity, BigDecimal unitPrice) {}

    public record DisbursementView(String description, BigDecimal amount, boolean vatable) {}

    public record LineView(UUID id, String serviceCode, String serviceName, Integer serviceVersion, String pricingModel, String description, BigDecimal units,
                           BigDecimal hours, BigDecimal complexityPct, boolean urgent, BigDecimal discountPct, BigDecimal beforeDiscount, BigDecimal discount,
                           BigDecimal professional, BigDecimal disbursements, BigDecimal vat, BigDecimal total, List<PartView> parts, List<ExtraView> extras,
                           List<DisbursementView> disbursementItems) {}

    public record QuoteView(UUID id, UUID clientId, String clientName, String quoteNumber, String title, String status, int priceBookVersion, String currency,
                            LocalDate validUntil, String notes, String terms, String requiredApprover, BigDecimal maxDiscountPct, BigDecimal professional,
                            BigDecimal discountTotal, BigDecimal disbursements, BigDecimal vat, BigDecimal vatRatePct, BigDecimal total, UUID createdBy,
                            UUID submittedBy, Instant submittedAt, UUID approvedBy, Instant approvedAt, String approvalNote, String approvalExceptionReason,
                            Instant sentAt, UUID decidedBy, Instant decidedAt, String decisionNote, String withdrawnReason, Instant createdAt, List<LineView> lines) {}

    public record QuoteSummary(UUID id, UUID clientId, String clientName, String quoteNumber, String title, String status, LocalDate validUntil, BigDecimal total,
                               String requiredApprover, BigDecimal maxDiscountPct, Instant createdAt) {}
}
