package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code actions} lists the corrective actions raised when a failed inspection was completed. */
public record InspectionResponse(
        UUID id, UUID templateId, String templateName, int templateRevision, UUID siteId, UUID assetId,
        UUID inspectorUserId, String inspectorName, String status, String result, String notes,
        int answered, int total, Instant startedAt, Instant completedAt, List<Result> results, List<UUID> actions
) {
    public record Result(UUID itemId, int position, String prompt, boolean critical, String outcome, String comment) {}
}
