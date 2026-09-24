package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateClientComplianceRegistrationRequest(
        String registrationNumber, @NotBlank String status,
        LocalDate issuedDate, LocalDate expiryDate, String notes
) {}
