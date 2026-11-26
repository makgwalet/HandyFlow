package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProgrammeNeedsDtos {
    private ProgrammeNeedsDtos() {
    }

    public record NeedRequest(@NotBlank String resourceType, @NotBlank String description, String matchText, @NotNull Integer quantity, Integer perLearners, @NotBlank String sourceNote) {
    }

    public record WithdrawRequest(@NotBlank String reason) {
    }

    public record NeedView(UUID id, UUID programmeVersionId, String resourceType, String description, String matchText, int quantity, Integer perLearners, String sourceNote,
                           String status, String withdrawnReason, Instant createdAt) {
    }

    public record VersionNeeds(UUID versionId, int versionNo, String versionStatus, boolean editable, List<NeedView> needs) {
    }

    public record ReadinessRow(UUID needId, String description, String resourceType, int required, int held, String status, String note) {
    }

    public record CohortReadiness(UUID cohortId, String siteName, long learners, String note, List<ReadinessRow> rows) {
    }

    public record SlotNeedRow(UUID needId, String description, int required, int booked, String status, String note) {
    }

    public record SlotNeeds(UUID slotId, int notCovered, List<SlotNeedRow> rows) {
    }

    public record CohortSlotNeeds(UUID cohortId, String note, List<SlotNeeds> slots) {
    }
}
