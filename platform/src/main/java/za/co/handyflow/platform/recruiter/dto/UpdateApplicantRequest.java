package za.co.handyflow.platform.recruiter.dto;
public record UpdateApplicantRequest(
        String phone, String location,
        String linkedinUrl, String portfolioUrl,
        String cvBase64, String cvFileName
) {}
