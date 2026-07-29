package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import za.co.handyflow.platform.crm.domain.model.CustomerCommunication.Direction;
import za.co.handyflow.platform.crm.domain.model.CustomerCommunication.Type;

import java.time.Instant;

public record LogCommunicationRequest(
        @NotNull Type      type,
        @NotNull Direction direction,
        @NotBlank String   summary,
        @NotNull @PastOrPresent Instant occurredAt
) {}