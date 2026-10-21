package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

public record CatalogueHistoryResponse(UUID id, String action, String detail, UUID actorId, Instant at) {}
