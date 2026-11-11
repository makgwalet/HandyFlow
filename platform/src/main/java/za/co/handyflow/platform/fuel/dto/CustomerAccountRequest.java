package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/** crmCustomerId is required on create and ignored on update (the account stays with its customer). */
public record CustomerAccountRequest(
        UUID crmCustomerId,
        @NotBlank String customerType,
        @NotNull @PositiveOrZero(message = "Credit limit cannot be negative") BigDecimal creditLimit,
        @NotNull @Min(0) @Max(365) Integer paymentTermsDays,
        @Size(max = 60) String licenceNumber,
        @Size(max = 500) String notes
) {}
