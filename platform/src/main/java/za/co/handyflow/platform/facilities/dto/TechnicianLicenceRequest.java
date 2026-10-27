package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TechnicianLicenceRequest(
        @NotBlank String licenceType, @Size(max = 120) String reference, @Size(max = 200) String issuedBy,
        LocalDate issueDate, LocalDate expiryDate
) {}
