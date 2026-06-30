package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportLostFirearmRequest(
        @NotBlank String notes
) {}
