package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.util.List;
import java.util.UUID;

/**
 * One result per operation, in the order sent. APPLIED: done now. DUPLICATE: already applied earlier, nothing
 * changed (treat as success). REJECTED: not applied; {@code errorCode} and {@code message} say why, and the
 * device should show it rather than retry unchanged.
 */
public record FmSyncResponse(List<Result> results) {
    public record Result(UUID opId, UUID targetId, String status, String errorCode, String message) {}
}
