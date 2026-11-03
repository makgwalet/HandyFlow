package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for lifecycle actions.
 * - reason:         required for RETURN, REOPEN, CANCEL; optional (recorded) otherwise
 * - closureNotes:   optional, CLOSE only
 * - lessonsLearned: required for CLOSE
 */
public record ProjectTransitionRequest(
        @Size(max = 2000, message = "Reason must not exceed 2 000 characters")
        String reason,

        @Size(max = 4000, message = "Closure notes must not exceed 4 000 characters")
        String closureNotes,

        @Size(max = 4000, message = "Lessons learned must not exceed 4 000 characters")
        String lessonsLearned
) {}