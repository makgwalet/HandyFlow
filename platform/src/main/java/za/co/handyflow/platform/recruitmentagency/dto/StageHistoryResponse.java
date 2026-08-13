package za.co.handyflow.platform.recruitmentagency.dto;

import java.time.Instant;
import java.util.UUID;

public record StageHistoryResponse(
        UUID id, String fromStage, String toStage, String notes, Instant changedAt
) {}