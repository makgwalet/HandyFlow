package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AssignToDetailRequest(
        @NotNull UUID guardId,
        @NotBlank String role,   // TEAM_LEADER | DRIVER | CPO | ADVANCE | COUNTER_SURVEILLANCE
        Instant assignmentStart  // null = now
) {}
