package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.util.UUID;

/** When a machine was last inspected and what was found. Machines never inspected are simply absent. */
public record LatestInspectionResponse(UUID assetId, Instant performedAt, String result) {}
