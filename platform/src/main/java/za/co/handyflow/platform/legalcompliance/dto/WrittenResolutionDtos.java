package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.ApprovalThreshold;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenChoice;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Written (circular) resolutions. While one is open, managers see who has answered but not how. */
public final class WrittenResolutionDtos {

    private WrittenResolutionDtos() {}

    public record CirculateRequest(@NotNull UUID committeeId, @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 8000) String text, @NotNull ApprovalThreshold threshold, @NotNull LocalDate deadline) {}

    public record RespondRequest(@NotNull WrittenChoice choice, @Size(max = 500) String reason) {}

    public record RecordRequest(@NotNull WrittenChoice choice, @Size(max = 500) String reason, @NotBlank @Size(min = 10, max = 500) String howReceived) {}

    public record CancelRequest(@NotBlank @Size(max = 2000) String reason) {}

    /** The counts are null while the resolution is open. */
    public record WrittenView(UUID id, UUID committeeId, String committeeName, String reference, String title, String text, ApprovalThreshold threshold, String thresholdText, LocalDate deadline,
                              WrittenStatus status, LocalDate circulatedOn, LocalDate closedOn, boolean closedAutomatically, String cancelReason, int memberCount, int answered,
                              Integer votesFor, Integer against, Integer abstained, Integer noResponse, boolean canClose, String closeProblem, boolean pastDeadline) {}

    /** choice and reason are filled only once the resolution is closed. */
    public record MemberRow(UUID memberId, String name, boolean hasAccount, boolean answered, Instant respondedAt, WrittenChoice choice, String reason, String recordedByName, String howReceived) {}

    public record WrittenDetail(WrittenView resolution, List<MemberRow> members) {}

    public record MyWritten(UUID resolutionId, String reference, String title, String text, String thresholdText, LocalDate deadline, WrittenStatus status, boolean open, WrittenChoice myChoice, String myReason, Instant respondedAt) {}
}
