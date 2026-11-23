package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

/** status: ACTIVE | SOLD | DECEASED | CULLED | TRANSFERRED_OUT */
public record ChangeAnimalStatusRequest(
        @NotBlank String status,
        /** Only needed to sell, cull or transfer out an animal that is still in its withdrawal period. */
        String withdrawalOverrideReason
) {
    public ChangeAnimalStatusRequest(String status) { this(status, null); }
}
