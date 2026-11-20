package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.List;

/** The trust control in force, and the history of changes to it (newest first, at most 20). */
public record LpTrustSettingsResponse(boolean selfApprovalAllowed, List<Change> history) {
    public record Change(boolean selfApprovalAllowed, String reason, String changedByName, Instant changedAt) {}
}
