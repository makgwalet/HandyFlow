package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for PATCH /api/v1/security/guards/{id}/status
 *
 * WHY require a note for SUSPENDED and TERMINATED?
 * Validated in GuardService — if status is SUSPENDED or TERMINATED and note
 * is blank, we reject with 400.  The domain records the note on the entity.
 * A note field on every status change (even ACTIVE reinstatement) provides
 * a full written history of what happened to this guard's employment.
 */
public record UpdateGuardStatusRequest(
        @NotBlank
        @Pattern(
                regexp = "ACTIVE|ON_LEAVE|SUSPENDED|UNDER_INVESTIGATION|TERMINATED",
                message = "status must be one of: ACTIVE, ON_LEAVE, SUSPENDED, UNDER_INVESTIGATION, TERMINATED"
        )
        String status,

        String note  // Required by service when status = SUSPENDED or TERMINATED
) {}
