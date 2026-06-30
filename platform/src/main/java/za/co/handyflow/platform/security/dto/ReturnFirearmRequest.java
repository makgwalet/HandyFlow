package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnFirearmRequest(
        @NotNull UUID witnessedByGuardId,
        String conditionNotes
) {}
