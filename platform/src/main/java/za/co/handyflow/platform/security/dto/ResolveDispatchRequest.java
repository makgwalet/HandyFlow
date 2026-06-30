package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveDispatchRequest(
        @NotBlank String outcome,   // RESOLVED | ESCALATED | FALSE_ALARM | NO_ACTION_NEEDED
        String notes
) {}
