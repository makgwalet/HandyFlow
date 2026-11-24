package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {

    private AssessmentDtos() {}

    public record JudgeRequest(@NotBlank @Size(max = 10) String control, @Size(max = 12) String workflow, @Size(max = 2000) String note) {}

    public record CompleteRequest(@Size(max = 4000) String summaryNote) {}

    public record WeightsRequest(int low, int medium, int high, int critical) {}

    public record WeightsView(int low, int medium, int high, int critical, boolean isDefault) {}

    public record EvidenceView(String documentType, boolean mandatory, String result, String detail, LocalDate expiresOn) {}

    public record ItemView(UUID obligationId, String code, String name, int versionNumber, String riskLevel, String applicability,
                           String evidenceStatus, String control, String workflow, String status, String displayStatus, String note,
                           UUID assessedBy, Instant assessedAt, List<EvidenceView> evidence) {}

    public record ScoreView(BigDecimal percent, int scored, int unscored, boolean provisional) {}

    public record ChangeView(String code, String from, String to, String reason) {}

    public record AssessmentSummary(UUID id, String status, LocalDate asOf, BigDecimal scorePercent, Integer scoredCount, Integer unscoredCount,
                                    Instant startedAt, Instant completedAt) {}

    public record AssessmentView(UUID id, UUID clientId, String status, LocalDate asOf, WeightsView weights, ScoreView score, String summaryNote,
                                 UUID startedBy, Instant startedAt, UUID completedBy, Instant completedAt, List<ItemView> items,
                                 List<String> completionBlockers, UUID comparedWith, List<ChangeView> changes) {}
}
