package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignWorkOrderRequest(@NotNull UUID userId) {}
