package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.UUID;

/** What the server decided about one entry. status is SYNCED, REJECTED or CONFLICT; duplicate is true when this submission id was already handled. */
public record FieldEntryResult(UUID id, UUID submissionId, String kind, UUID assetId, String assetName, Instant capturedAt, Instant receivedAt, String status,
                               String reason, boolean duplicate, String reviewDecision, String reviewNote, Instant reviewedAt) {}
