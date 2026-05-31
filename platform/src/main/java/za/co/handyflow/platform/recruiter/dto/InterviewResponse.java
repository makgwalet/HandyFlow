package za.co.handyflow.platform.recruiter.dto;
import java.time.Instant;
import java.util.UUID;
public record InterviewResponse(
        UUID id, String interviewType, Instant scheduledAt,
        String interviewerName, String outcome, String notes,
        Integer score, Instant createdAt
) {}