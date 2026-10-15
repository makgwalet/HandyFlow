package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Link every unlinked deployment that carries this typed site and client text. At least one id is needed. */
public record ResolveLinksRequest(@NotNull String legacySite, String legacyClient, UUID siteId, UUID customerId, UUID projectId) {}
