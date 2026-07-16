package za.co.handyflow.platform.identity.dto;

import jakarta.validation.constraints.Email;

public record UpdateBillingContactRequest(
        @Email String billingEmail,
        String         billingContactName,
        String         billingPhone
) {}
