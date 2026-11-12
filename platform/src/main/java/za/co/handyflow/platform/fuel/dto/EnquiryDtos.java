package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class EnquiryDtos {
    private EnquiryDtos() {}

    public record EnquiryRequest(UUID accountId, @Size(max = 120) String contactName, @Size(max = 40) String contactPhone, @Size(max = 160) String contactEmail,
                                 @NotBlank @Size(max = 30) String fuelType, @NotNull @Positive(message = "Litres must be greater than zero") BigDecimal litres,
                                 LocalDate requestedDate, @Size(max = 10) String source, @Size(max = 500) String notes) {}

    /** Raises the quotation. Price empty means the contract or standard price, exactly as when quoting directly. */
    public record QuoteRequest(UUID siteId, @jakarta.validation.constraints.PositiveOrZero BigDecimal pricePerLitre, LocalDate quoteValidUntil,
                               @Size(max = 60) String customerReference) {}
    public record CloseRequest(@NotBlank @Size(max = 300) String reason) {}

    public record EnquiryResponse(UUID id, String enquiryNumber, UUID accountId, String accountName, String contactName, String contactPhone, String contactEmail,
                                  String fuelType, BigDecimal litres, LocalDate requestedDate, String source, String status, UUID quoteOrderId,
                                  String quoteOrderNumber, String decisionReason, String notes, Instant createdAt, Instant decidedAt) {}

    public record Revision(int revisionNo, UUID siteId, String fuelType, BigDecimal litres, BigDecimal pricePerLitre, LocalDate requestedDate,
                           LocalDate quoteValidUntil, String customerReference, String notes, UUID recordedBy, Instant recordedAt,
                           BigDecimal priceChange) {}
    public record Acceptance(int revisionNo, String acceptedBy, String channel, String reference, LocalDate acceptedOn, UUID recordedBy, Instant recordedAt) {}
    public record OrderHistory(List<Revision> revisions, Acceptance acceptance) {}
    /** Sent with a confirmation when the customer's acceptance should be recorded. */
    public record AcceptanceRequest(@NotBlank @Size(max = 120) String acceptedBy, @NotBlank @Size(max = 12) String channel,
                                    @Size(max = 80) String reference, LocalDate acceptedOn) {}
}
