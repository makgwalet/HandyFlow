package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ScheduleInterviewRequest(
        @NotBlank String  interviewType,
        Instant scheduledAt,
        UUID    interviewerId,      // primary interviewer — unchanged
        String  interviewerName,
        String  location,          // venue address (IN_PERSON) or meeting link (VIDEO) — optional
        List<PanelistRequest> panelists,  // additional interviewers, alongside the primary — optional
        UUID    roundTemplateId    // links to a RecJobInterviewRound — optional, null for ad-hoc interviews
) {}