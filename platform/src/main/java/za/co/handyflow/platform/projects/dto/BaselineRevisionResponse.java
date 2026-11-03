package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectBaselineRevision;

import java.time.Instant;
import java.util.UUID;

/** detailJson is a JSON document of exactly what moved (old and new values); clients parse it. */
public record BaselineRevisionResponse(
        int version, UUID changeOrderId, String summary, String detailJson,
        String appliedByName, Instant appliedAt
) {
    public static BaselineRevisionResponse of(ProjectBaselineRevision r) {
        return new BaselineRevisionResponse(r.getVersion(), r.getChangeOrderId(), r.getSummary(),
                r.getDetailJson(), r.getAppliedByName(), r.getAppliedAt());
    }
}