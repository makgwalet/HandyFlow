package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record WalkInRequest(
        UUID tierId,
        @NotBlank @Size(max = 200) String fullName,
        @Email @Size(max = 254) String email,
        @Size(max = 50) String phone,
        @Size(max = 200) String company,
        @Size(max = 500) String dietaryRequirements,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal amountPaid,
        @Size(max = 200) String location
) {}