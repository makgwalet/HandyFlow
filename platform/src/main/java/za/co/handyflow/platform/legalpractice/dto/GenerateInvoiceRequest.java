package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code matterId} is nullable for a retainer-only invoice (mirrors
 * {@code LpInvoice.matterId}'s own nullability). {@code timeEntryIds}/
 * {@code disbursementIds} may both be empty for a pure FIXED_FEE or
 * retainer invoice — {@code LpBillingService.generateInvoice()} then
 * expects the fixed amount to come from {@code fixedFeeAmount}.
 */
public record GenerateInvoiceRequest(
        @NotNull UUID clientId,
        UUID matterId,
        List<UUID> timeEntryIds,
        List<UUID> disbursementIds,
        java.math.BigDecimal fixedFeeAmount,
        LocalDate dueDate,
        String description,
        String notes
) {}
