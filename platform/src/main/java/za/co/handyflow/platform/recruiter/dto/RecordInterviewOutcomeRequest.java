package za.co.handyflow.platform.recruiter.dto;

public record RecordInterviewOutcomeRequest(
        String outcome, String notes, Integer score
) {}