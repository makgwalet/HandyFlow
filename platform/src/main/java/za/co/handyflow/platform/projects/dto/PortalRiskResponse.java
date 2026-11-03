package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectRisk;

/**
 * Client-safe risk view — no mitigation plan, owner, description or OHSA flag.
 * (A per-risk "client visible" flag arrives with the risk engine chunk; until then
 * only open RED risks are shown, as before.)
 */
public record PortalRiskResponse(String title, String category, String rating, String status) {
    public static PortalRiskResponse of(ProjectRisk r) {
        return new PortalRiskResponse(r.getTitle(), r.getCategory(), r.getRating(), r.getStatus());
    }
}