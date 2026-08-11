// security/dto/ShiftSupervisorActionRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Shared request body for the three supervisor-initiated shift interrupts:
 * dismiss-no-show, close-overtime, pull. All three require a written reason
 * for the same audit-trail rationale as GuardService's SUSPENDED/TERMINATED
 * note requirement -- these are operational decisions made on a guard's
 * behalf and need to be explainable after the fact.
 */
public record ShiftSupervisorActionRequest(
        @NotBlank String reason
) {}