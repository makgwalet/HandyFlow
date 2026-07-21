package za.co.handyflow.platform.recruiter.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record SubmitApplicationRequest(
        @NotBlank  String firstName,
        @NotBlank  String lastName,
        @Email @NotBlank String email,
        String phone,
        String location,
        String linkedinUrl,
        String portfolioUrl,
        String cvBase64,       // base64-encoded PDF
        String cvFileName,
        String source,         // CAREERS_PAGE, LINKEDIN, REFERRAL etc.
        String referrerName    // free text, only meaningful when source=REFERRAL — candidate has no way to know internal user IDs
) {}