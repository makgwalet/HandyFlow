package za.co.handyflow.platform.billing.dto;

import java.time.Instant;

public record CancelPreviewResponse(
        String moduleKey,
        String moduleName,
        int affectedRecords,
        String message,
        Instant accessUntil    // when access actually ends after cancellation
) {}