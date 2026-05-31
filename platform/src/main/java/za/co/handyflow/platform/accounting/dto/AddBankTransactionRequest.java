package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AddBankTransactionRequest(
        @NotNull LocalDate transactionDate,
        @NotBlank String description,
        String reference,
        @NotNull BigDecimal amount,
        @NotBlank String transactionType
) {}