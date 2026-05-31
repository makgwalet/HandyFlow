package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.UUID;

public record RegisterGuestRequest(
        UUID tierId,
        UUID customerId,
        @NotBlank String fullName,
        String email,
        String phone,
        String company,
        String dietaryRequirements,
        BigDecimal amountPaid,
        String notes
) {}