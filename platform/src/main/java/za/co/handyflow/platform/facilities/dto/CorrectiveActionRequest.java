package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** A manually logged finding. {@code sourceType} defaults to MANUAL; CERTIFICATE may carry the certificate id as sourceId. */
public record CorrectiveActionRequest(
        @NotNull UUID siteId, UUID assetId, @NotBlank @Size(max = 1000) String description, String priority,
        @NotNull LocalDate dueDate, String sourceType, UUID sourceId
) {}
