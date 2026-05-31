package za.co.handyflow.platform.recruiter.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ApplicationResponse(
        UUID   id, UUID jobId, String jobTitle,
        UUID   applicantId, String applicantName, String applicantEmail,
        String applicantPhone, boolean hasCv,
        String stage, String source, Integer score,
        String notes, String rejectionReason,
        UUID   hrEmployeeId,
        List<InterviewResponse> interviews,
        List<StageHistoryResponse> history,
        Instant appliedAt, Instant stageChangedAt, Instant hiredAt
) {}
