package za.co.handyflow.platform.events.dto;

import java.util.List;

/**
 * Is the event ready to run? Advisory — it never blocks going live — but every check that fails is
 * a specific thing someone can go and fix.
 */
public record ReadinessResponse(
        boolean ready,                         // every check passed
        List<ReadinessCheck> checks,
        List<PhaseProgress> checklistProgress  // per phase, in the order an event moves through them
) {
    public record ReadinessCheck(String code, String label, boolean passed, String detail) {}

    public record PhaseProgress(String phase, int total, int done, int requiredOpen) {}
}