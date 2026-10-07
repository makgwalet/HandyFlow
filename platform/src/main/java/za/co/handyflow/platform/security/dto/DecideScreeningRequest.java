// security/dto/DecideScreeningRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

/** Reviewer sign-off on a screening that has a result. A note is required when not clearing. */
public record DecideScreeningRequest(
        @NotBlank String decision,   // CLEARED | NOT_CLEARED
        String note
) {}
