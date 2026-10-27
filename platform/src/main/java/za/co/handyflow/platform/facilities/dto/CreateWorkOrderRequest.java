package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Direct creation by a manager (skips the request/approval step; the work order starts OPEN).
 * {@code requiresVerification} is optional (default false): when true, completion needs evidence and
 * an independent verifier before the job counts as COMPLETED.
 */
public record CreateWorkOrderRequest(
        @NotNull UUID siteId, UUID assetId, @NotBlank String category, String priority,
        @NotBlank String description, String reportedBy, LocalDate scheduledDate,
        Boolean requiresVerification
) {}