package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Give EITHER {@code subtotal} (the VAT-exclusive amount to credit; VAT is added at the invoice's rate) OR
 * {@code creditFullBalance = true} (credit everything still outstanding). {@code reason} is required.
 */
public record CreateFmCreditNoteRequest(
        @DecimalMin("0.01") BigDecimal subtotal,
        Boolean creditFullBalance,
        @NotBlank @Size(max = 500) String reason
) {}
