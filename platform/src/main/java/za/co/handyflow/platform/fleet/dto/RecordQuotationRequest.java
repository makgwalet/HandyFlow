package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Records the supplier's quotation and puts the order in QUOTED, awaiting approval. Attach the quote document separately. */
public record RecordQuotationRequest(
        @NotNull @Positive BigDecimal amount,
        @Size(max = 255) String supplier,
        @Size(max = 100) String quotationRef,
        LocalDate quotationDate,
        LocalDate validUntil,
        String notes
) {}