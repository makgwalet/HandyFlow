package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPinRequest(
        @NotBlank @Size(max = 500) String reason    // mandatory audit field
) {}
