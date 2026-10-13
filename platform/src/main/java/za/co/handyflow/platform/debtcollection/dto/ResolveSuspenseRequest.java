package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ResolveSuspenseRequest(@NotNull UUID caseId, String note) {}
