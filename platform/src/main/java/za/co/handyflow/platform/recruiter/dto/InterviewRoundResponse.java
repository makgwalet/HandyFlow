package za.co.handyflow.platform.recruiter.dto;
import java.util.UUID;
public record InterviewRoundResponse(
        UUID id, String name, int sequence, String description
) {}