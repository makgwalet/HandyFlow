package za.co.handyflow.platform.recruiter.dto;
import java.time.Instant;
import java.util.UUID;
public record PublicApplicationResponse(
        UUID    applicationId, String jobTitle, String companyName,
        String  applicantName, String stage,
        String  stageLabel,    // human-readable e.g. "Under Review"
        Instant appliedAt, Instant stageChangedAt
) {}