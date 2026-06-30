package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CheckoutResourceRequest(
        @NotBlank String resourceType,   // RADIO | KEY | FIREARM | VEHICLE | OTHER
        @NotBlank String resourceRef,    // e.g. "Radio R-014"
        UUID witnessedBy,              // optional
        String notes
) {}
