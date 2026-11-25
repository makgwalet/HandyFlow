package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AgreementDtos {

    private AgreementDtos() {}

    /** Anything left out is filled in from the quote: billed once, for its professional fees, starting today. */
    public record TermsRequest(@Size(max = 255) String title, LocalDate startDate, LocalDate endDate, @Size(max = 12) String billingFrequency,
                               BigDecimal billingAmount, LocalDate firstBillingDate, Boolean autoRenew, Integer noticeDays,
                               @Size(max = 4000) String notes, @Size(max = 4000) String terms) {}

    public record CreateRequest(@NotNull UUID quoteId, @Valid TermsRequest terms) {}

    public record NoteRequest(@Size(max = 2000) String note) {}

    public record ReasonRequest(@Size(max = 2000) String reason) {}

    public record EndRequest(LocalDate endDate, @Size(max = 2000) String reason) {}

    public record LineView(UUID id, String serviceCode, String serviceName, Integer serviceVersion, String pricingModel, String description,
                           BigDecimal professional) {}

    public record ScheduleEntry(LocalDate date, BigDecimal amount) {}

    public record QuoteOption(UUID id, String quoteNumber, String title, BigDecimal professional, BigDecimal total, Instant acceptedAt) {}

    public record Summary(UUID id, UUID clientId, String clientName, UUID quoteId, String quoteNumber, String agreementNumber, String title, String status,
                          LocalDate startDate, LocalDate endDate, String billingFrequency, BigDecimal billingAmount, LocalDate nextBillingDate,
                          String attention) {}

    public record View(UUID id, UUID clientId, String clientName, UUID quoteId, String quoteNumber, String agreementNumber, String title, String status,
                       LocalDate startDate, LocalDate endDate, String billingFrequency, BigDecimal billingAmount, LocalDate firstBillingDate,
                       boolean autoRenew, int noticeDays, String notes, String terms, BigDecimal quotedProfessional, BigDecimal quotedTotal,
                       String currency, BigDecimal contractValue, String comparisonNote, String attention, LocalDate nextBillingDate,
                       boolean scheduleTruncated, List<ScheduleEntry> schedule, List<LineView> lines, List<String> actions, boolean canEdit,
                       UUID activatedBy, Instant activatedAt, String activationNote, String suspendedReason, UUID endedBy, Instant endedAt,
                       String endReason, Instant createdAt, Long version) {}
}
