package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.util.UUID;

/** One earlier tag of an animal and when it was replaced. */
public record TagHistoryResponse(UUID id, UUID animalId, String oldTag, String newTag, String changedByName, Instant changedAt) {}
