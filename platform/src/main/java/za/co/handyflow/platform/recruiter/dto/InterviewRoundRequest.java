package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
public record InterviewRoundRequest(
        @NotBlank String name,
        int sequence,
        String description
) {}