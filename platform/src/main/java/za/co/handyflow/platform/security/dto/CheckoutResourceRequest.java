package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CheckoutResourceRequest(
        @NotBlank String resourceType,   // RADIO | KEY | FIREARM | VEHICLE | OTHER
        @NotBlank String resourceRef,    // e.g. "Radio R-014"
        UUID witnessedBy,              // optional
        String notes,
        UUID clientRef                 // optional, phone-generated: a retry with the same id returns the first check-out
) {
    public CheckoutResourceRequest(String resourceType, String resourceRef, UUID witnessedBy, String notes) {
        this(resourceType, resourceRef, witnessedBy, notes, null);
    }
}
