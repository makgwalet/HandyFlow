package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
public record ScheduleInterviewRequest(
        @NotBlank String  interviewType,
        Instant scheduledAt,
        UUID    interviewerId,
        String  interviewerName
) {}