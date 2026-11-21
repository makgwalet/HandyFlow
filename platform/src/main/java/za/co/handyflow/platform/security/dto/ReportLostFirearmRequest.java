package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportLostFirearmRequest(
        @NotBlank String notes,
        /** SAPS case (CAS) number if the loss has already been reported. Otherwise record it within 24 hours. */
        @Size(max = 60) String sapsCaseNumber
) {}
