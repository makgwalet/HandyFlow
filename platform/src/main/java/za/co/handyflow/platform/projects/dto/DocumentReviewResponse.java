package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectDocumentReview;

import java.time.Instant;

public record DocumentReviewResponse(String action, String comment, String actorName, Instant occurredAt) {
    public static DocumentReviewResponse of(ProjectDocumentReview r) {
        return new DocumentReviewResponse(r.getAction(), r.getComment(), r.getActorName(), r.getOccurredAt());
    }
}