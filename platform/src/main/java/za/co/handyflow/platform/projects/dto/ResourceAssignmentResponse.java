package za.co.handyflow.platform.projects.dto;

import java.util.List;

/**
 * Response body for POST /{projectId}/resources.
 *
 * Contains both the assigned resource and any double-booking warnings.
 * An empty {@code warnings} list means the assignment is clean.
 *
 * WHY INCLUDE WARNINGS IN THE RESPONSE BODY (NOT AS HTTP 409):
 * ─────────────────────────────────────────────────────────────
 * A double-booking is a warning, not an error.  The business rule is:
 * managers may override conflicts and proceed.  Returning 409 would block
 * the assignment entirely, forcing the manager to first delete the conflicting
 * record — poor UX.
 *
 * HTTP 201 + warnings: "the assignment was saved AND here's what you should know."
 * The frontend checks warnings.length > 0 and shows a toast notification.
 *
 * @param resource  The newly saved resource assignment
 * @param warnings  Human-readable double-booking descriptions (empty if clean)
 */
public record ResourceAssignmentResponse(
        ResourceResponse resource,
        List<String>     warnings
) {
}
