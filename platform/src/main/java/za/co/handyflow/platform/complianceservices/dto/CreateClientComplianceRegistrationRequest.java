package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateClientComplianceRegistrationRequest(
        @NotBlank String authority, @NotBlank String registrationType, String registrationNumber,
        LocalDate issuedDate, LocalDate expiryDate, String notes
) {}
