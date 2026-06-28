package za.co.handyflow.platform.security.dto;

import java.time.Instant;

/**
 * Request body for PUT /api/v1/security/shifts/{id}
 *
 * WHY allow only notes and endAt?
 * Guard and site cannot change on an existing shift — that would change the
 * nature of the deployment.  If those need to change, cancel this shift and
 * create a new one.  startAt also cannot be changed once a shift is SCHEDULED
 * (the guard already knows their start time).  Only endAt can be extended
 * (overtime, site request) and notes can be updated at any time.
 *
 * WHY is endAt nullable?
 * A caller who only wants to update notes should not be forced to re-send the
 * endAt.  If endAt is null, the service leaves the existing endAt unchanged.
 */
public record UpdateShiftRequest(
        String  notes,  // null = keep existing
        Instant endAt   // null = keep existing; must be after current startAt if provided
) {}
