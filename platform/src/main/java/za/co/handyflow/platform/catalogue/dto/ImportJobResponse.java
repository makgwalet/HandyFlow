package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

public record ImportJobResponse(UUID id, String fileName, String mode, int totalRows, int created, int updated, int unchanged, int skipped, int failed, UUID createdBy, Instant createdAt) {}
