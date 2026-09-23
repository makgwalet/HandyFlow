package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateComplianceRegistrationRequest(
        @NotBlank String authority, @NotBlank String registrationType, String registrationNumber,
        LocalDate issuedDate, LocalDate expiryDate, String notes
) {}
