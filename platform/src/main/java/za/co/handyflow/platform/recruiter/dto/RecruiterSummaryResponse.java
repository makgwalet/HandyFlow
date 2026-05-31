package za.co.handyflow.platform.recruiter.dto;
public record RecruiterSummaryResponse(
        long openJobs, long draftJobs, long filledJobs,
        long newApplications, long inScreening, long inInterview,
        long offersMade, long hiredThisMonth
) {}