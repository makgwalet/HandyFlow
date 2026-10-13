package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignWorkItemRequest(@NotNull UUID userId, String userName, String reason) {}
