package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Replaces the user's client set. An empty set removes the restriction (user becomes unrestricted). */
public record SetFmClientAccessRequest(@NotNull Set<UUID> clientIds) {}