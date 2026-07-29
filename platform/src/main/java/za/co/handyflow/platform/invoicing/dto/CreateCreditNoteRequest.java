package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCreditNoteRequest(
        @NotBlank String reason,
        String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,   // ex-VAT amount to credit
        BigDecimal vatRate                                 // defaults to 15% if null
) {}