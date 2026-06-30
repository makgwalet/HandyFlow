package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddRouteCheckpointRequest(
        @NotNull UUID checkpointId,
        @Min(0) int sequence
) {}