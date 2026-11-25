package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {
    private AssessmentDtos() {
    }

    public record InstrumentRequest(@NotNull UUID programmeVersionId, UUID moduleId, @NotBlank String title, @NotBlank String assessmentType,
                                    BigDecimal maxMark, String guidance, Boolean requiresModeration) {
    }

    public record InstrumentUpdate(UUID moduleId, @NotBlank String title, @NotBlank String assessmentType, BigDecimal maxMark, String guidance,
                                   Boolean requiresModeration) {
    }

    public record ApproveRequest(String overrideReason) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record InstrumentEvent(UUID id, String action, String note, UUID actedBy, Instant actedAt) {
    }

    public record InstrumentView(UUID id, String instrumentNo, UUID programmeId, String programmeTitle, UUID programmeVersionId, Integer versionNo,
                                 UUID moduleId, String moduleTitle, String title, String assessmentType, BigDecimal maxMark, String guidance,
                                 boolean requiresModeration, String status, Instant approvedAt, String approvalOverrideReason, String retiredReason,
                                 List<String> nextStatuses, List<InstrumentEvent> history) {
    }
}
