package za.co.handyflow.platform.facilities.dto;

import java.util.List;
import java.util.UUID;

/** {@code restricted=false} means the user has no site restriction (full access to all sites). */
public record SiteAccessResponse(UUID userId, boolean restricted, List<UUID> siteIds) {}