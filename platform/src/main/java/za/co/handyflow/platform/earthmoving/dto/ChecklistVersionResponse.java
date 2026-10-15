package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChecklistVersionResponse(UUID id, String inspectionType, int versionNo, String status, LocalDate effectiveFrom, boolean requiresSignoff,
                                       String note, Instant createdAt, Instant publishedAt, List<ChecklistQuestionDto> questions) {}
