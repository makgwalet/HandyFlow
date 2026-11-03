package za.co.handyflow.platform.projects.dto;

import java.util.List;

/**
 * Result of the project-closure checklist.
 *
 * @param ready true when every BLOCKING check passes (warnings never block closure)
 */
public record ClosureReadinessResponse(
        boolean ready,
        List<Check> checks
) {
    /**
     * @param code        stable machine-readable id, e.g. OPEN_TASKS
     * @param description human-readable explanation
     * @param blocking    true = failing this prevents closure; false = warning only
     * @param count       number of offending records
     * @param passed      count == 0
     */
    public record Check(String code, String description, boolean blocking, long count, boolean passed) {}
}