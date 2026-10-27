package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Replaces the user's site set. An empty set removes the restriction (user becomes unrestricted). */
public record SetSiteAccessRequest(@NotNull Set<UUID> siteIds) {}