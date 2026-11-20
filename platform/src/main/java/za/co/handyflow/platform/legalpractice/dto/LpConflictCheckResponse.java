package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LpConflictCheckResponse(
        UUID id,
        UUID clientId,
        String clientName,
        String status,
        boolean truncated,
        List<Subject> subjects,
        List<Hit> hits,
        String runByName,
        Instant runAt,
        String decidedByName,
        Instant decidedAt,
        String decisionReason,
        boolean selfReviewed,
        Instant validUntil,
        UUID matterId,
        /** True when this check can open a matter right now. */
        boolean usable
) {
    public record Subject(String name, String idNumber, String role) {}
    public record Hit(String subjectName, String matchKind, String matchedKind, String matchedName, String matchedRole,
                      String matchedDetail, UUID matchedClientId, UUID matchedMatterId) {}
}
