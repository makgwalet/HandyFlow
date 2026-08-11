// security/dto/CloneDetailResult.java
package za.co.handyflow.platform.security.dto;

import java.util.List;

/**
 * WHY a summary alongside the new detail, rather than just returning
 * ProtectionDetailResponse?
 * Cloning re-validates every team assignment through the same hard gates
 * assignToDetail() already enforces (guard schedulable, CP vetting tier
 * sufficient for the principal's threat level) rather than blindly copying
 * rows. A guard who covered the VC's last campus visit might have let their
 * vetting lapse since then -- silently dropping them from the new detail
 * without telling anyone would be worse than the manual re-entry this
 * feature is meant to save. skippedAssignments surfaces exactly that.
 */
public record CloneDetailResult(
        ProtectionDetailResponse detail,
        int teamMembersCloned,
        List<String> skippedAssignments,   // human-readable reasons, e.g. "Guard J. Smith (DRIVER): PSiRA expired"
        int itineraryStopsCloned
) {}