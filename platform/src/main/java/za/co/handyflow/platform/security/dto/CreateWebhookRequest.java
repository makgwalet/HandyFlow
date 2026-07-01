package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record CreateWebhookRequest(
        @NotBlank String name,
        @NotBlank String endpointUrl,
        @NotBlank String eventTypesJson,  // ["ALARM_EVENT","SHIFT_MISSED"]
        UUID branchId
) {}
