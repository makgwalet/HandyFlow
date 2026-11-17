package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    // ── live transactions (sales, refunds, voids) ────────────────────────────

    /**
     * One live transaction. kind is SALE, REFUND or VOID. A SALE needs pumpLabel, litres and amount. A REFUND or VOID needs originalExternalId (the sale it
     * reverses, sent under the same source) and a reason; a VOID reverses the whole sale, a REFUND part or all of it. amount is always more than 0.
     */
    public record IncomingTransaction(@NotBlank @Size(max = 100) String externalId,
                                      @NotBlank @Pattern(regexp = "SALE|REFUND|VOID", message = "kind must be SALE, REFUND or VOID") String kind,
                                      Instant occurredAt,
                                      @Size(max = 40) String pumpLabel,
                                      @Positive @DecimalMax("100000") BigDecimal litres,
                                      @NotNull @Positive @DecimalMax("1000000") BigDecimal amount,
                                      @Pattern(regexp = "CASH|CARD|ACCOUNT|FLEET|OTHER", message = "tender must be CASH, CARD, ACCOUNT, FLEET or OTHER") String tender,
                                      @Size(max = 100) String originalExternalId,
                                      @Size(max = 300) String reason) {}

    /** The transactions of one shift. shiftExternalId is the externalId the finished shift is (or will be) sent under, with the same source. */
    public record IncomingTransactions(@NotBlank @Size(max = 40) String source,
                                       @NotBlank @Size(max = 100) String shiftExternalId,
                                       @NotEmpty @Size(max = 200) @Valid List<IncomingTransaction> transactions) {}

    /** outcome: STAGED, DUPLICATE (same content already received) or CONFLICT (same externalId, different content: not stored). problems are listed, not refused. */
    public record TransactionOutcome(String externalId, String outcome, List<String> problems) {}

    public record TransactionIngestResult(String shiftExternalId, List<TransactionOutcome> results) {}

    public record TransactionView(UUID id, String externalId, String kind, String originalExternalId, String pumpLabel, BigDecimal litres, BigDecimal amount,
                                  String tender, Instant occurredAt, String reason, String status, List<String> problems, UUID adjustmentId) {}

    /**
     * What the sender's transactions add up to for a shift, to compare with the shift itself. netByTender is sales less refunds and voids per tender
     * (tender "UNSPECIFIED" when the sender gave none). meterLitres is what the staged shift's meters say (null until the shift is received).
     * Nothing here changes the shift: its own takings stay the authority.
     */
    public record TransactionSummary(int sales, BigDecimal salesLitres, BigDecimal salesAmount, BigDecimal refundsAmount, BigDecimal voidsAmount, BigDecimal netAmount,
                                     Map<String, BigDecimal> netByTender, BigDecimal meterLitres, BigDecimal litresDifference, int notApplied) {}

    public record ShiftTransactions(List<TransactionView> transactions, TransactionSummary summary) {}
}
