package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.ApprovalThreshold;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenMode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CharterDtos {

    private CharterDtos() {}

    public record RecordCharterRequest(@Size(max = 120) String charterReference, @NotNull LocalDate adoptedOn, @NotNull LocalDate effectiveFrom, boolean castingVote,
                                       @NotNull WrittenMode writtenMode, boolean quorumAfterRecusal, @Min(51) @Max(100) int specialPercent,
                                       @NotBlank @Size(min = 10, max = 1000) String reason) {}

    public record CharterVersion(UUID id, int versionNo, String charterReference, LocalDate adoptedOn, LocalDate effectiveFrom, boolean castingVote, WrittenMode writtenMode,
                                 boolean quorumAfterRecusal, int specialPercent, String reason, String recordedBy, boolean inForce) {}

    /** recorded=false: no charter on file, so the earlier house rules apply (summary says so in words). */
    public record CharterView(UUID committeeId, boolean recorded, Integer currentVersion, boolean castingVote, WrittenMode writtenMode, boolean quorumAfterRecusal, int specialPercent,
                              List<ApprovalThreshold> allowedWrittenThresholds, List<String> summary, List<CharterVersion> history) {}
}
