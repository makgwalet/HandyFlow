package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnFirearmRequest(
        @NotNull UUID witnessedByGuardId,
        String conditionNotes,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(2000) Integer ammoRoundsReturned,
        String ammoUnaccountedReason
) {}
