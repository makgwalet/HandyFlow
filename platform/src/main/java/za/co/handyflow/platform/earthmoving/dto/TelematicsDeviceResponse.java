package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.UUID;

public record TelematicsDeviceResponse(UUID id, UUID assetId, String assetName, String fleetNumber, String providerCode, String externalId, String label,
                                       boolean active, Instant mappedAt, Instant unmappedAt, String unmapReason) {}
