package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 8e (D03): the provider-neutral shape a pump or POS system sends for one finished shift, and the review-queue views.
 * There is no vendor or device code here: whoever builds the sender maps their data onto this shape.
 */
public final class PosImportDtos {
    private PosImportDtos() {}

    /** Pumps are named as they are set up in Fuel (Forecourt > Pumps); names are compared ignoring case. */
    public record IncomingReading(@NotBlank @Size(max = 40) String pumpName,
                                  @NotNull @PositiveOrZero BigDecimal openingMeter,
                                  @NotNull @PositiveOrZero BigDecimal closingMeter,
                                  @PositiveOrZero BigDecimal testLitres) {}

    /** source + externalId identify the shift. Sending the same pair again is safe: it is never staged twice. */
    public record IncomingShift(@NotBlank @Size(max = 40) String source,
                                @NotBlank @Size(max = 100) String externalId,
                                @NotBlank @Size(max = 120) String attendantName,
                                Instant openedAt, Instant closedAt,
                                @NotNull @PositiveOrZero BigDecimal cash, @NotNull @PositiveOrZero BigDecimal card,
                                @NotNull @PositiveOrZero BigDecimal account, @NotNull @PositiveOrZero BigDecimal other,
                                @PositiveOrZero BigDecimal fleetCard,
                                @Size(max = 300) String notes,
                                @NotEmpty @Size(max = 40) @Valid List<IncomingReading> readings) {}

    /** status is PENDING, ACCEPTED or REJECTED. duplicate is true when this shift was already received with the same content. */
    public record IngestResult(UUID importId, String status, boolean duplicate, List<String> problems) {}

    public record ImportView(UUID id, String source, String externalId, String status, Instant receivedAt, List<String> problems,
                             IncomingShift shift, UUID decidedBy, Instant decidedAt, String decisionNote, UUID shiftId) {}

    public record AcceptRequest(@Size(max = 500) String varianceReason, @Size(max = 500) String note) {}

    public record RejectRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

    public record KeyRequest(@NotBlank @Size(max = 150) String name) {}

    public record KeyView(UUID id, String name, String keyPrefix, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {}

    /** The plaintext key is shown once, here, and is not stored. */
    public record CreatedKey(KeyView key, String apiKey) {}

    public record AliasRequest(@NotBlank @Size(max = 40) String source, @NotBlank @Size(max = 40) String alias, @jakarta.validation.constraints.NotNull UUID pumpId) {}

    public record AliasView(UUID id, String source, String alias, UUID pumpId, String pumpName, Instant createdAt) {}
}
