package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.UUID;

public final class WorkpaperReviewDtos {
    private WorkpaperReviewDtos() {}

    public record TextRequest(String text) {}
    public record LinkRequest(String type, UUID id) {}

    public record NoteResponse(UUID id, UUID workpaperId, String note, UUID raisedBy, Instant raisedAt, String status, String response,
                               Instant respondedAt, Instant clearedAt, int timesReturned) {}
    public record LinkResponse(UUID id, UUID workpaperId, String type, UUID linkId, Instant linkedAt) {}
}
