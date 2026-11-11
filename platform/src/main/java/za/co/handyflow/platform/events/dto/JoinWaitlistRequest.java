package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record JoinWaitlistRequest(
        UUID tierId,                                  // null = waiting for any place at the event
        UUID customerId,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,   // required: this is how the ticket reaches them on promotion
        @Size(max = 50) String phone,
        @Size(max = 200) String company,
        @Size(max = 500) String dietaryRequirements
) {}