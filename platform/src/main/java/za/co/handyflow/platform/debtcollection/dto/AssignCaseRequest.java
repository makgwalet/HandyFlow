package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCaseRequest(@NotNull UUID userId, String userName) {}
