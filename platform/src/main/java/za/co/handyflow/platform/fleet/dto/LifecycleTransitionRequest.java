package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleStatus;

/**
 * Simple lifecycle moves: ORDERED → ACQUIRED → ACTIVE, ACTIVE ⇄ IMPOUNDED, and retiring.
 * Stolen/missing, write-off, sold/transferred/disposed, and recovery have their own
 * endpoints because they capture required detail.
 */
public record LifecycleTransitionRequest(
        @NotNull VehicleLifecycleStatus target,   // ACQUIRED | ACTIVE | IMPOUNDED | RETIRED
        @Size(max = 500) String reason,
        @Size(max = 4000) String note
) {}