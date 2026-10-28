package za.co.handyflow.platform.recruiter.dto;

import java.time.Instant;
import java.util.UUID;

public final class RecruiterCalendarDtos {
    private RecruiterCalendarDtos() {}

    public record CalendarInterview(UUID id, UUID applicationId, String candidate, String jobTitle, String type, Instant scheduledAt,
                                    int durationMinutes, String outcome, String interviewerName, String location) {}
}
