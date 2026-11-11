package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Reserve one ticket on a paid event and start paying for it. The hold length is
 * set by the server (app.events.payments.hold-minutes), never by the caller.
 * Email is required: the ticket is sent there once payment is confirmed.
 */
public record StartCheckoutRequest(
        @NotNull UUID tierId,
        UUID customerId,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 50) String phone,
        @Size(max = 200) String company,
        @Size(max = 500) String dietaryRequirements
) {}