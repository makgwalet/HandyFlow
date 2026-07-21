package za.co.handyflow.platform.recruiter.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record InterviewResponse(
        UUID id, String interviewType, Instant scheduledAt,
        String interviewerName, String outcome, String notes,
        Integer score, String location,
        List<PanelistResponse> panelists,
        UUID roundTemplateId, String roundName, Integer roundSequence,
        Instant reminderSentAt, String rescheduleReason, UUID rescheduledFromInterviewId,
        Instant createdAt
) {}