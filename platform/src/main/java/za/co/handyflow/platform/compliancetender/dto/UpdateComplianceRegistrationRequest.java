package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateComplianceRegistrationRequest(
        String registrationNumber, @NotBlank String status,
        LocalDate issuedDate, LocalDate expiryDate, String notes
) {}
