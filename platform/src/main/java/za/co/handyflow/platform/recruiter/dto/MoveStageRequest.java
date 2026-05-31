package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.NotBlank;
public record MoveStageRequest(
        @NotBlank String stage,    // SCREENING|INTERVIEW|ASSESSMENT|OFFER|HIRED|REJECTED
        String notes,
        String rejectionReason
) {}