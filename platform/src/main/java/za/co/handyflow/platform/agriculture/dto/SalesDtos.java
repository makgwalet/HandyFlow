package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request and response shapes for linking sales to production (ADR-001, W2). Agriculture stores NO sales or money: revenue is computed live
 * from the invoice (ex-VAT, net of credit notes, only while issued). Held together because they only make sense together.
 */
public final class SalesDtos {

    private SalesDtos() {}

    /** An invoice line that counts as revenue, with how much of it is already allocated to production. */
    public record SaleLineResponse(
            UUID invoiceId, String invoiceNumber, String invoiceStatus, UUID customerId, String customerName, LocalDate issuedOn,
            String currency, UUID lineItemId, String description, String unit, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal lineTotal,           // ex-VAT, before credit notes
            BigDecimal netRevenue,          // ex-VAT, after the line's share of credit notes
            BigDecimal allocatedQuantity,   // across all farms
            BigDecimal remainingQuantity
    ) {}

    /** One target's part of an invoice line. {@code headCount} is optional livestock context (a single animal is one head). */
    public record SaleShare(@NotBlank String targetType, @NotNull UUID targetId, @NotNull @Positive BigDecimal quantity, @Positive Integer headCount) {}

    public record AllocateSaleRequest(
            @NotNull UUID invoiceLineId,
            LocalDate soldOn,               // defaults to the invoice's issue date
            String notes,
            @Valid @NotNull List<SaleShare> allocations
    ) {}

    public record SalesAllocationResponse(
            UUID id, UUID farmId, UUID invoiceId, String invoiceNumber, UUID invoiceLineId, String description, String customerName,
            String targetType, UUID targetId, BigDecimal quantity, String unit, Integer headCount, LocalDate soldOn, String notes, String status,
            String invoiceStatus,
            BigDecimal revenue,             // live, ex-VAT, net of credit notes; 0 when not counted
            boolean counted,                // false when the invoice is no longer revenue (cancelled, or gone)
            String notCountedReason,
            Instant createdAt
    ) {}

    public record TargetRevenue(String targetType, UUID targetId, BigDecimal quantity, BigDecimal revenue) {}

    /** Revenue of the active allocations; {@code notCountedCount} allocations sit on invoices that are no longer revenue and add nothing. */
    public record SalesTotalsResponse(BigDecimal revenue, int allocationCount, int notCountedCount, List<TargetRevenue> byTarget) {}
}
