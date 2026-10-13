package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationScope;

import java.util.UUID;

/** fromUserId is required for scope FROM_USER (move everything off a leaver). */
public record RunAllocationRequest(@NotNull AllocationScope scope, UUID fromUserId) {}
