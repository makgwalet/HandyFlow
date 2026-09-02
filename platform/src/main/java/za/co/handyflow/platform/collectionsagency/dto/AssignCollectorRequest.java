package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCollectorRequest(@NotNull UUID collectorId) {}
