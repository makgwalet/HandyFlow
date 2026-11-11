package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportIncidentRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotBlank String category,          // MEDICAL, SECURITY, SAFETY, FACILITY, LOGISTICS, GUEST_COMPLAINT, SUPPLIER, WEATHER, OTHER
        @NotBlank String severity,          // LOW | MEDIUM | HIGH | CRITICAL
        @Size(max = 200) String location
) {}