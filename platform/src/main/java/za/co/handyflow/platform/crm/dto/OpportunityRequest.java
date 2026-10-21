package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Create (customerId required) or edit (customerId ignored) an opportunity's details. Stage changes have their own request. */
public record OpportunityRequest(
        UUID customerId,
        @NotBlank @Size(max = 255) String name,
        @NotNull BigDecimal amount,
        LocalDate expectedCloseDate,
        UUID ownerId,
        @Size(max = 4000) String notes
) {}
