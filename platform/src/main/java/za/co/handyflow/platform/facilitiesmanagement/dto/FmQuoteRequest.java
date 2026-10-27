package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code clientId}, {@code siteId}, {@code assetId} and {@code workOrderId} are fixed when the quote is created (they are
 * ignored on update). {@code workOrderId} optionally ties the quote to a client request it prices; without one, accepting
 * the quote creates the work order. Prices are VAT-exclusive; VAT is added from the company's billing profile.
 */
public record FmQuoteRequest(
        @NotNull UUID clientId, @NotNull UUID siteId, UUID assetId, UUID workOrderId,
        @NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
        @NotNull LocalDate validUntil, @Size(max = 1000) String internalNote,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines
) {
    public record Line(@NotBlank @Size(max = 500) String description, @NotNull @DecimalMin("0.001") @DecimalMax("1000000") BigDecimal quantity,
                       @NotNull @DecimalMin("0.00") @DecimalMax("100000000") BigDecimal unitPrice) {}
}
