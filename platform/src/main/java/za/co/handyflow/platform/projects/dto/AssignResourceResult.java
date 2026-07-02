package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectResource;

import java.util.List;

/**
 * Result of assigning a resource — includes both the saved entity and any
 * double-booking warnings.
 *
 * WHY A RESULT RECORD INSTEAD OF THROWING AN EXCEPTION?
 * ───────────────────────────────────────────────────────
 * A double-booking is a WARNING, not an error.  The business rule is:
 * "let the manager decide" — which means the assignment MUST be saved
 * even if there are conflicts.
 *
 * Throwing an exception (e.g. 409 Conflict) would block the assignment
 * entirely, requiring the manager to delete the conflicting one first —
 * bad UX and wrong semantics.
 *
 * Returning this record allows the controller to:
 *   1. HTTP 201 Created (the assignment was saved)
 *   2. Include { "warnings": ["..."] } in the response body
 *   3. The frontend shows a toast: "Resource assigned — note: 1 scheduling conflict"
 *
 * This is the industry-standard approach used by tools like Planview and MS Project.
 *
 * @param resource  The newly assigned ProjectResource entity.
 * @param warnings  Human-readable descriptions of any double-bookings detected.
 *                  Empty list if no conflicts exist.
 */
public record AssignResourceResult(
        ProjectResource resource,
        List<String>    warnings
) {
    /** True if this assignment has at least one double-booking conflict. */
    public boolean hasConflicts() {
        return warnings != null && !warnings.isEmpty();
    }
}
