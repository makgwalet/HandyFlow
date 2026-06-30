package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignDriverRequest(
        @NotNull UUID guardId
) {}
