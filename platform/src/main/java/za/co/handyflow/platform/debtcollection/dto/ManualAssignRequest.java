package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** override=true (supervisors only) pushes past capacity/availability limits; a reason is then mandatory and the override is audited. */
public record ManualAssignRequest(@NotNull UUID userId, boolean override, String reason) {}
