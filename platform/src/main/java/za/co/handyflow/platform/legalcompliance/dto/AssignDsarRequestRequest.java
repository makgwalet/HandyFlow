package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignDsarRequestRequest(@NotNull UUID userId, @NotBlank String userName) {}
