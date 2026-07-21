package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Only reason is required. Everything else is an optional override —
 * null means "carry over from the interview being replaced" (see
 * RecruiterService.rescheduleInterview() for the exact merge logic).
 * scheduledAt is nullable deliberately: "postponed, no new time yet" is
 * a real, valid outcome of a reschedule, not just "moved to a specific
 * new slot".
 */
public record RescheduleInterviewRequest(
        @NotBlank String reason,
        Instant scheduledAt,
        String  interviewType,
        UUID    interviewerId,
        String  interviewerName,
        String  location,
        List<PanelistRequest> panelists,
        UUID    roundTemplateId
) {}