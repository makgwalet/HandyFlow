package za.co.handyflow.platform.billing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangePlanRequest(
        @NotNull UUID planId
) {}